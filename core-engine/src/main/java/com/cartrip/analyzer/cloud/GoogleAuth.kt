package com.cartrip.analyzer.cloud

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

object GoogleAuth {
    const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val TOKEN_SCOPE = "oauth2:$SCOPE_SHEETS $SCOPE_DRIVE_FILE"

    private fun options(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_SHEETS), Scope(SCOPE_DRIVE_FILE))
            .build()

    fun client(context: Context): GoogleSignInClient = GoogleSignIn.getClient(context, options())

    fun lastAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /**
     * Blocking OAuth access-token fetch — call on a background thread.
     * May throw UserRecoverableAuthException (needs user consent), GoogleAuthException, or IOException.
     */
    fun token(context: Context, account: Account): String =
        GoogleAuthUtil.getToken(context, account, TOKEN_SCOPE)
}
