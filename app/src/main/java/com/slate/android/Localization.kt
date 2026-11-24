// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

// Keep internal identifiers and reader labels stable; translate only UI labels.
@Composable
internal fun tr(value:String):String {
    val id=when(value){
        "Dynamic colors" -> R.string.ui_001
        "Use your wallpaper colors" -> R.string.ui_002
        "Welcome to Slate" -> R.string.ui_003
        "Understand your storage and device in one place." -> R.string.ui_004
        "Storage analyzer" -> R.string.ui_005
        "Find large files and explore folders." -> R.string.ui_006
        "Device information" -> R.string.ui_007
        "Hardware, battery, display and Android." -> R.string.ui_008
        "Your files and measurements stay on your device." -> R.string.ui_009
        "Permissions" -> R.string.ui_010
        "Only grant the access needed for the tools you use." -> R.string.ui_011
        "Storage access" -> R.string.ui_012
        "Allow" -> R.string.ui_013
        "No additional permission required." -> R.string.ui_014
        "You can continue without storage access and enable it later." -> R.string.ui_015
        "Make it yours" -> R.string.ui_016
        "Choose your appearance. You can change it in Settings." -> R.string.ui_017
        "Home dashboard" -> R.string.ui_018
        "Scan history" -> R.string.ui_019
        "Clear" -> R.string.ui_020
        "No completed scans yet." -> R.string.ui_021
        "Folder changes" -> R.string.ui_022
        "No comparable previous folders." -> R.string.ui_023
        "Close" -> R.string.ui_024
        "Clear scan history?" -> R.string.ui_025
        "Files are not affected." -> R.string.ui_026
        "Cancel" -> R.string.ui_027
        "Internal storage" -> R.string.ui_028
        "Temperature" -> R.string.ui_029
        "App storage" -> R.string.ui_030
        "Cleanup" -> R.string.ui_031
        "Quick folders" -> R.string.ui_032
        "Since last session" -> R.string.ui_033
        "Storage insights" -> R.string.ui_034
        "History" -> R.string.ui_035
        "Low storage" -> R.string.ui_036
        "Analyze storage for insights" -> R.string.ui_037
        "No large folders found" -> R.string.ui_038
        "External storage" -> R.string.ui_039
        "Settings" -> R.string.ui_040
        "Appearance" -> R.string.ui_041
        "Manage" -> R.string.ui_042
        "Home" -> R.string.ui_043
        "Storage" -> R.string.ui_044
        "Device" -> R.string.ui_045
        "Allow access to shared storage to measure folders. Slate does not delete anything during a scan." -> R.string.ui_046
        "Grant access" -> R.string.ui_047
        "Partially refreshed index" -> R.string.ui_048
        "Refresh all" -> R.string.ui_049
        "Saved results" -> R.string.ui_050
        "Refresh" -> R.string.ui_051
        "Scan again" -> R.string.ui_052
        "Select visible" -> R.string.ui_053
        "Deleting selected items…" -> R.string.ui_054
        "User files" -> R.string.ui_055
        "Android shared files" -> R.string.ui_056
        "Protected files are not fully measurable." -> R.string.ui_057
        "Folders" -> R.string.ui_058
        "Largest files" -> R.string.ui_059
        "Search files" -> R.string.ui_060
        "Incomplete" -> R.string.ui_061
        "Measured size: " -> R.string.ui_062
        "This cannot be undone. Inaccessible or incomplete items are skipped." -> R.string.ui_063
        "Delete permanently" -> R.string.ui_064
        "Deletion result" -> R.string.ui_065
        "Done" -> R.string.ui_066
        "Open" -> R.string.ui_067
        "Share" -> R.string.ui_068
        "Updates" -> R.string.ui_069
        "Checks for updates when Slate opens." -> R.string.ui_070
        "Check for updates" -> R.string.ui_071
        "A newer version of Slate is available." -> R.string.ui_072
        "Checking for updates…" -> R.string.ui_073
        "Install" -> R.string.ui_074
        "Download & install" -> R.string.ui_075
        "Tests" -> R.string.ui_076
        "Net power at battery" -> R.string.ui_077
        "Live sensors" -> R.string.ui_078
        "No supported sensors" -> R.string.ui_079
        "Tap to change color" -> R.string.ui_080
        "Drag across the screen" -> R.string.ui_081
        "Advanced cache cleanup" -> R.string.ui_082
        "Ask Android to free additional space from app caches. App data is not targeted. System-managed cache cleanup is device-wide and does not use folder exclusions." -> R.string.ui_083
        "Clean with root" -> R.string.ui_084
        "Trim app caches?" -> R.string.ui_085
        "Clean" -> R.string.ui_086
        "Usage access" -> R.string.ui_087
        "Allow Slate to read installed app sizes and cache." -> R.string.ui_088
        "Cache · " -> R.string.ui_089
        "Shared files may overlap with file analysis." -> R.string.ui_090
        "User apps" -> R.string.ui_091
        "System apps" -> R.string.ui_092
        "Search apps" -> R.string.ui_093
        "Version " -> R.string.ui_094
        "Installed " -> R.string.ui_095
        "Uninstall" -> R.string.ui_096
        "App settings" -> R.string.ui_097
        "Scheduled cleanup review" -> R.string.ui_098
        "Scans temporary files and empty folders while charging. Review before deletion." -> R.string.ui_099
        "Could not schedule scan" -> R.string.ui_100
        "Storage access required" -> R.string.ui_101
        "Remnants" -> R.string.ui_102
        "Possible app remnants" -> R.string.ui_103
        "Folders in Android/media with no installed package match. Review before removing." -> R.string.ui_104
        "Find" -> R.string.ui_105
        "No candidates found" -> R.string.ui_106
        "Review" -> R.string.ui_107
        "Scan limit reached" -> R.string.ui_108
        "Keep at least one copy in each group." -> R.string.ui_109
        "This cannot be undone." -> R.string.ui_110
        "Delete" -> R.string.ui_111
        "Storage exclusions" -> R.string.ui_112
        "Paths relative to internal storage." -> R.string.ui_113
        "Add" -> R.string.ui_114
        "Enter a valid relative folder or file path." -> R.string.ui_115
        "Granted" -> R.string.ui_116
        "Not granted" -> R.string.ui_117
        "Required to scan shared files and folders." -> R.string.ui_118
        "Finish setup" -> R.string.ui_119
        "Continue" -> R.string.ui_120
        "Update available" -> R.string.ui_121
        "Slate updates" -> R.string.ui_122
        "Later" -> R.string.ui_123
        "Download verified. Tap Install to continue." -> R.string.ui_124
        "Confirm the update in Android's installer." -> R.string.ui_125
        "Unable to open installer" -> R.string.ui_126
        "Allow app installation to continue." -> R.string.ui_127
        "Allow Slate to install apps, then return to continue." -> R.string.ui_128
        "You're up to date." -> R.string.ui_129
        "Unable to check for updates" -> R.string.ui_130
        "Download verified." -> R.string.ui_131
        "Download failed" -> R.string.ui_132
        "Cancelled" -> R.string.ui_133
        "Used" -> R.string.ui_134
        "Available" -> R.string.ui_135
        "Total" -> R.string.ui_136
        "Battery" -> R.string.ui_137
        "Hardware" -> R.string.ui_138
        "Overview" -> R.string.ui_139
        "System" -> R.string.ui_140
        "Network" -> R.string.ui_141
        "Sensors" -> R.string.ui_142
        "Camera" -> R.string.ui_143
        "Cameras" -> R.string.ui_144
        "Memory" -> R.string.ui_145
        "Discharging" -> R.string.ui_146
        "Charging" -> R.string.ui_147
        "Full" -> R.string.ui_148
        "Not charging" -> R.string.ui_149
        "Unavailable" -> R.string.ui_150
        "Offline" -> R.string.ui_151
        "Used space increased" -> R.string.ui_152
        "Space recovered" -> R.string.ui_153
        "View storage" -> R.string.ui_154
        "Analyze storage" -> R.string.ui_155
        "Files" -> R.string.ui_156
        "Contents" -> R.string.ui_157
        "Size ↓" -> R.string.ui_158
        "Size ↑" -> R.string.ui_159
        "Name" -> R.string.ui_160
        "Type" -> R.string.ui_161
        "Date ↓" -> R.string.ui_162
        "Largest first" -> R.string.ui_163
        "Smallest first" -> R.string.ui_164
        "File type" -> R.string.ui_165
        "Newest first" -> R.string.ui_166
        "No matching items" -> R.string.ui_167
        "Folder not accessible" -> R.string.ui_168
        "Back" -> R.string.ui_169
        "Search" -> R.string.ui_170
        "Change layout" -> R.string.ui_171
        "Clear selection" -> R.string.ui_172
        "Delete selected" -> R.string.ui_173
        "Remove exclusion" -> R.string.ui_174
        "About" -> R.string.ui_175
        "Expand" -> R.string.ui_176
        "Collapse" -> R.string.ui_177
        "Show less" -> R.string.ui_178
        "Show all" -> R.string.ui_179
        "No information available" -> R.string.ui_180
        "Loading…" -> R.string.ui_181
        "Processor" -> R.string.ui_182
        "Graphics" -> R.string.ui_183
        "Display" -> R.string.ui_184
        "Thermals" -> R.string.ui_185
        "CPU frequencies" -> R.string.ui_186
        "GPU activity" -> R.string.ui_187
        "Frequency" -> R.string.ui_188
        "Maximum frequency" -> R.string.ui_189
        "Utilization" -> R.string.ui_190
        "Thermal throttling status" -> R.string.ui_191
        "Resolution" -> R.string.ui_192
        "Refresh rate" -> R.string.ui_193
        "Level" -> R.string.ui_194
        "Status" -> R.string.ui_195
        "Health (system status)" -> R.string.ui_196
        "Power source" -> R.string.ui_197
        "Technology" -> R.string.ui_198
        "Voltage" -> R.string.ui_199
        "Instant current (+ charging)" -> R.string.ui_200
        "Average current (+ charging)" -> R.string.ui_201
        "Remaining charge" -> R.string.ui_202
        "Time to full (system estimate)" -> R.string.ui_203
        "Estimated full capacity" -> R.string.ui_204
        "Design capacity (reported)" -> R.string.ui_205
        "Full capacity (reported)" -> R.string.ui_206
        "Capacity health (reported)" -> R.string.ui_207
        "Charge cycles" -> R.string.ui_208
        "Uptime" -> R.string.ui_209
        "Total RAM" -> R.string.ui_210
        "Available RAM" -> R.string.ui_211
        "Used RAM (estimate)" -> R.string.ui_212
        "Low memory" -> R.string.ui_213
        "Since boot" -> R.string.ui_214
        "Awake" -> R.string.ui_215
        "Deep sleep (estimate)" -> R.string.ui_216
        "SoC manufacturer" -> R.string.ui_217
        "Detected cores" -> R.string.ui_218
        "Supported ABIs" -> R.string.ui_219
        "Physical mode" -> R.string.ui_220
        "Supported refresh rates" -> R.string.ui_221
        "Density" -> R.string.ui_222
        "HDR types (Android IDs)" -> R.string.ui_223
        "GPU renderer" -> R.string.ui_224
        "Vendor" -> R.string.ui_225
        "GL extensions (current context)" -> R.string.ui_226
        "Vulkan support" -> R.string.ui_227
        "Vulkan API (reported)" -> R.string.ui_228
        "Manufacturer" -> R.string.ui_229
        "Brand" -> R.string.ui_230
        "Model" -> R.string.ui_231
        "Product" -> R.string.ui_232
        "Board" -> R.string.ui_233
        "Android version" -> R.string.ui_234
        "API level" -> R.string.ui_235
        "Security patch" -> R.string.ui_236
        "Build ID" -> R.string.ui_237
        "Build number" -> R.string.ui_238
        "Build type" -> R.string.ui_239
        "Kernel release" -> R.string.ui_240
        "Baseband" -> R.string.ui_241
        "Bootloader" -> R.string.ui_242
        "Maximum range" -> R.string.ui_243
        "Power" -> R.string.ui_244
        "Minimum delay" -> R.string.ui_245
        "Wake-up" -> R.string.ui_246
        "Sensor resolution" -> R.string.ui_247
        "Megapixels" -> R.string.ui_248
        "Sensor size" -> R.string.ui_249
        "Apertures" -> R.string.ui_250
        "Focal lengths" -> R.string.ui_251
        "ISO range" -> R.string.ui_252
        "Flash" -> R.string.ui_253
        "Metadata" -> R.string.ui_254
        "Capacity" -> R.string.ui_255
        "Mount path" -> R.string.ui_256
        "Connection" -> R.string.ui_257
        "Internet validated" -> R.string.ui_258
        "Metered" -> R.string.ui_259
        "Interface" -> R.string.ui_260
        "Addresses" -> R.string.ui_261
        "Estimated downlink" -> R.string.ui_262
        "Estimated uplink" -> R.string.ui_263
        "Wi-Fi signal" -> R.string.ui_264
        "Link speed" -> R.string.ui_265
        "Transmit link speed" -> R.string.ui_266
        "Receive link speed" -> R.string.ui_267
        "Wi-Fi standard" -> R.string.ui_268
        "Operator" -> R.string.ui_269
        "Radio access" -> R.string.ui_270
        "Roaming" -> R.string.ui_271
        "Signal level" -> R.string.ui_272
        "Active connection" -> R.string.ui_273
        "Cores" -> R.string.ui_274
        "Current" -> R.string.ui_275
        "Time to full" -> R.string.ui_276
        "Capacity health" -> R.string.ui_277
        "Full capacity" -> R.string.ui_278
        "Estimated capacity" -> R.string.ui_279
        "Charging power" -> R.string.ui_280
        "Discharge power" -> R.string.ui_281
        "Battery power" -> R.string.ui_282
        "Battery temperature" -> R.string.ui_283
        "Deep sleep" -> R.string.ui_284
        "Battery details" -> R.string.ui_285
        "Health" -> R.string.ui_286
        "Graphics & display" -> R.string.ui_287
        "GPU utilization" -> R.string.ui_288
        "Maximum clock" -> R.string.ui_289
        "Refresh rates" -> R.string.ui_290
        "Thermals & connection" -> R.string.ui_291
        "Thermal status" -> R.string.ui_292
        "Quick details" -> R.string.ui_293
        "Build" -> R.string.ui_294
        "Duplicates" -> R.string.ui_295
        "Temporary" -> R.string.ui_296
        "Temporary files" -> R.string.ui_297
        "Empty" -> R.string.ui_298
        "Empty folders" -> R.string.ui_299
        "Compare file contents." -> R.string.ui_300
        "Temporary extensions, unchanged for 7 days." -> R.string.ui_301
        "Folders without files." -> R.string.ui_302
        "Deleting…" -> R.string.ui_303
        "Off" -> R.string.ui_304
        "Daily" -> R.string.ui_305
        "Weekly" -> R.string.ui_306
        "Device status" -> R.string.ui_307
        "Quick tools" -> R.string.ui_308
        "Dark" -> R.string.ui_309
        "Light" -> R.string.ui_310
        "AMOLED" -> R.string.ui_311
        "Device tests" -> R.string.ui_312
        "Display test" -> R.string.ui_313
        "Touch test" -> R.string.ui_314
        "Sensor test" -> R.string.ui_315
        "Display colors" -> R.string.ui_316
        "Touch response" -> R.string.ui_317
        "Good" -> R.string.ui_318
        "Disconnected" -> R.string.ui_319
        "Mobile" -> R.string.ui_320
        "Unplugged" -> R.string.ui_321
        "Wireless" -> R.string.ui_322
        "Max texture size" -> R.string.ui_323
        "Max cube map size" -> R.string.ui_324
        "Max renderbuffer size" -> R.string.ui_325
        "Combined texture units" -> R.string.ui_326
        "Vertex attributes" -> R.string.ui_327
        "Fragment uniform vectors" -> R.string.ui_328
        "Vertex uniform vectors" -> R.string.ui_329
        "Images" -> R.string.category_images
        "Videos" -> R.string.category_videos
        "Audio" -> R.string.category_audio
        "Documents" -> R.string.category_documents
        "Archives" -> R.string.category_archives
        "Installers" -> R.string.category_installers
        "Other files" -> R.string.category_other_files
        "Hide categories" -> R.string.category_hide_categories
        "File categories" -> R.string.category_file_categories
        "Category details require a complete index." -> R.string.category_category_details_require_a_complete_index
        "App totals are separate; unmeasured space may include system and private files." -> R.string.category_app_totals_are_separate_unmeasured_space_may_include_system_and_private_files
        "Unable to open file" -> R.string.msg_0
        "Unable to share file" -> R.string.msg_1
        "Scan failed" -> R.string.msg_2
        "Scan cancelled" -> R.string.msg_3
        "Authorized" -> R.string.msg_4
        "Permission required" -> R.string.msg_5
        "Not running" -> R.string.msg_6
        "Clean with Shizuku" -> R.string.msg_7
        "Authorize Shizuku" -> R.string.msg_8
        "Could not request Shizuku permission" -> R.string.msg_9
        "Cleanup unavailable" -> R.string.msg_10
        "Hide exclusions" -> R.string.msg_11
        "Size unavailable or incomplete" -> R.string.msg_12
        "Protected remnants" -> R.string.protected_0
        "Android/data and obb · only empty folders can be removed." -> R.string.protected_1
        "Protected storage unavailable" -> R.string.protected_2
        "Not empty" -> R.string.protected_3
        "Delete empty folder?" -> R.string.protected_4
        "Folder changed or access denied" -> R.string.protected_5
        "Authorize Shizuku in Settings." -> R.string.protected_6
        "Apps" -> R.string.dashboard_v2_0
        "All" -> R.string.dashboard_v2_1
        "User" -> R.string.dashboard_v2_2
        "App" -> R.string.dashboard_v2_3
        "Data" -> R.string.dashboard_v2_4
        "Cache" -> R.string.dashboard_v2_5
        "App folders" -> R.string.dashboard_v2_6
        "Private data" -> R.string.dashboard_v2_7
        "No direct access" -> R.string.dashboard_v2_8
        "Reported app cache" -> R.string.dashboard_v2_9
        "Review app cache" -> R.string.dashboard_v2_10
        "Cache ↓" -> R.string.dashboard_v2_11
        else -> 0
    }
    return if(id==0)value else stringResource(id)
}
