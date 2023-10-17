/*
 * Copyright 2023 S57 ApS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.s57io.webviewmultidisplay

object Constants {
    const val WEB_URL = "https://experiments.s57.io/webview-multidisplay-demo"
    const val LOG_TAG = "webviewmultidisplay-app"

    /**
     * ACCEPT_SELF_SIGNED_CERTIFICATES
     * Default: false
     * Warning: For testing only. Do not ever enable this in production code.
     *          Accepting all certificates is insecure.
     */
    const val ACCEPT_SELF_SIGNED_CERTIFICATES = false
}
