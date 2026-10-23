package ir.amirab.downloader.ftp

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.IOException
import java.util.UUID

/**
 * Windows user-scoped, local-machine credential storage for authenticated FTP.
 *
 * Uses JNA 5.19.1 and the official CredWriteW/ReadW/DeleteW/Free API in
 * Advapi32.dll. It deliberately stores no secret in the downloaded-item JSON
 * or FTP .part sidecars. The credential reference is a random, opaque UUID.
 * WinCred at-rest security does NOT encrypt FTP itself on the network.
 *
 * The desktop GUI and local CLI persist only this opaque reference. Native
 * round-trip, restart, and deletion behavior are exercised on Windows CI.
 */
class WindowsFtpCredentialVault {
    companion object {
        private const val NAMESPACE = "PersonalDownloadManager/FTP/"
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2
        private const val ERROR_NOT_FOUND = 1168
        private const val MAX_CREDENTIAL_BLOB_BYTES = 2560
        private val ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }

    private val native: WinCredApi by lazy {
        require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Windows Credential Manager is available on Windows only"
        }
        Native.load("Advapi32", WinCredApi::class.java, W32APIOptions.UNICODE_OPTIONS)
    }

    /** Returns an opaque ID, never the password. */
    fun save(username: String, password: String): String {
        require(username.isNotBlank() && username.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "FTP user name is invalid"
        }
        require(password.isNotEmpty()) { "FTP password must not be empty" }
        val bytes = password.toByteArray(Charsets.UTF_16LE)
        try {
            require(bytes.size <= MAX_CREDENTIAL_BLOB_BYTES) { "FTP credential is too long" }
            val ref = UUID.randomUUID().toString()
            val secret = Memory(bytes.size.toLong())
            try {
                secret.write(0L, bytes, 0, bytes.size)
                val credential = NativeCredential().apply {
                    Flags = 0
                    Type = CRED_TYPE_GENERIC
                    TargetName = WString(NAMESPACE + ref)
                    UserName = WString(username)
                    CredentialBlob = secret
                    CredentialBlobSize = bytes.size
                    Persist = CRED_PERSIST_LOCAL_MACHINE
                }
                if (!native.CredWriteW(credential, 0)) {
                    throw nativeFailure("save")
                }
                return ref
            } finally {
                secret.clear()
            }
        } finally {
            bytes.fill(0)
        }
    }

    fun read(reference: String): FtpSessionCredentials? {
        val target = safeTarget(reference)
        val output = PointerByReference()
        if (!native.CredReadW(WString(target), CRED_TYPE_GENERIC, 0, output)) {
            val code = Native.getLastError()
            if (code == ERROR_NOT_FOUND) return null
            throw IOException("Windows Credential Manager read failed (code $code)")
        }
        val pointer = output.value ?: throw IOException("Windows credential read returned null")
        try {
            val credential = NativeCredential(pointer)
            require(credential.Type == CRED_TYPE_GENERIC) { "Unexpected Windows credential type" }
            val length = credential.CredentialBlobSize
            require(length in 2..MAX_CREDENTIAL_BLOB_BYTES && length % 2 == 0) {
                "Invalid Windows credential blob length"
            }
            val raw = credential.CredentialBlob?.getByteArray(0L, length)
                ?: throw IOException("Windows credential blob is missing")
            return try {
                val username = credential.UserName?.toString().orEmpty()
                require(username.isNotBlank()) { "Windows credential user name missing" }
                FtpSessionCredentials(username, String(raw, Charsets.UTF_16LE))
            } finally {
                raw.fill(0)
            }
        } finally {
            native.CredFree(pointer)
        }
    }

    /** Deletion is scoped to an opaque credential ID from our application namespace. */
    fun delete(reference: String): Boolean {
        val target = safeTarget(reference)
        if (native.CredDeleteW(WString(target), CRED_TYPE_GENERIC, 0)) return true
        val code = Native.getLastError()
        if (code == ERROR_NOT_FOUND) return false
        throw IOException("Windows Credential Manager delete failed (code $code)")
    }

    private fun safeTarget(reference: String): String {
        require(ID.matches(reference)) { "Invalid credential reference" }
        return NAMESPACE + reference
    }

    private fun nativeFailure(action: String): IOException =
        IOException("Windows Credential Manager $action failed (code ${Native.getLastError()})")
}

interface WinCredApi : StdCallLibrary {
    fun CredWriteW(credential: NativeCredential, flags: Int): Boolean
    fun CredReadW(target: WString, type: Int, flags: Int, result: PointerByReference): Boolean
    fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean
    fun CredFree(credential: Pointer)
}

/**
 * CREDENTIALW structure matches wincred.h field order and native pointer layout.
 * FIELD ORDER MUST stay aligned with the actual Windows ABI.
 */
@Structure.FieldOrder(
    "Flags", "Type", "TargetName", "Comment", "LastWritten",
    "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount",
    "Attributes", "TargetAlias", "UserName"
)
class NativeCredential : Structure {
    @JvmField var Flags: Int = 0
    @JvmField var Type: Int = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWritten: WinBase.FILETIME = WinBase.FILETIME()
    @JvmField var CredentialBlobSize: Int = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist: Int = 0
    @JvmField var AttributeCount: Int = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null

    constructor() : super()
    constructor(pointer: Pointer) : super(pointer) { read() }
}
