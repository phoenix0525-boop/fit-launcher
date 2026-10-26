package tw.fitcar.launcher.check

object ResultValidity {
    fun matches(recordVersion: String, recordContext: String, version: String, context: String): Boolean =
        recordVersion.isNotBlank() && recordContext.isNotBlank() && recordVersion == version && recordContext == context
}
