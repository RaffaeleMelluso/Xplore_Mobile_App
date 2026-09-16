package com.example.progmob_progetto_melluso.auth

/**
 * Translates Google Sign-In result codes into domain [AuthError]s.
 *
 * Why it exists: the account chooser reports failures through
 * `ApiException.statusCode`, a type owned by the Google Play services SDK.
 * Keeping the translation here means the Activity does not decide what a code
 * means, the domain stays free of provider types, and the mapping can be tested
 * with plain JUnit.
 *
 * How it works: only the codes the app can act on get a dedicated case;
 * anything else stays [AuthError.Unknown]. A dismissed chooser is deliberately
 * NOT an error, which is why [toAuthError] returns null for it.
 */
object GoogleSignInErrorMapper {

    /**
     * Google Play services refused the request because the app is not
     * recognised: the signing certificate of this build is not registered for
     * the OAuth client of the project.
     */
    private const val STATUS_DEVELOPER_ERROR = 10

    /** Google Play services could not reach the network. */
    private const val STATUS_NETWORK_ERROR = 7

    /** The user closed the chooser without picking an account. */
    private const val STATUS_SIGN_IN_CANCELLED = 12501

    /**
     * Translates a chooser status code into a domain error.
     *
     * @param statusCode the code carried by the failed sign-in task.
     * @return the domain error to show, or null when nothing should be shown
     *   because the user simply dismissed the chooser.
     */
    fun toAuthError(statusCode: Int): AuthError? = when (statusCode) {
        STATUS_SIGN_IN_CANCELLED -> null
        STATUS_DEVELOPER_ERROR -> AuthError.NotConfigured
        STATUS_NETWORK_ERROR -> AuthError.Network
        else -> AuthError.Unknown()
    }
}
