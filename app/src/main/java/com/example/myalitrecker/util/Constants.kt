package com.example.myalitrecker.util

object Constants {
    const val WEB_CLIENT_ID = "111758533324-5nabn5sdhik11lrvlms0hq4fmrmghifo.apps.googleusercontent.com"
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    // Broad search query for AliExpress confirmation, shipping and consolidation emails
    const val GMAIL_ALIEXPRESS_QUERY = "from:aliexpress.com OR from:notice.aliexpress.com OR subject:AliExpress"
}
