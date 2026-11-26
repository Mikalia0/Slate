// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File

internal fun isAddonArchive(name:String):Boolean=name.lowercase().let{n->listOf(".7z",".tar",".tgz",".tar.gz",".tar.xz",".txz",".tar.bz2",".tbz2",".tar.zst",".tzst",".zst",".gz",".xz",".bz2",".lz4",".rar").any{n.endsWith(it)}}
