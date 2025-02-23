package com.restartaltair.zipsaver

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查并请求 MANAGE_EXTERNAL_STORAGE 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 请求 MANAGE_EXTERNAL_STORAGE 权限
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }

        setContent {
            APKSaverApp(intent)
        }
    }
}

@Composable
fun APKSaverApp(intent: Intent) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("等待接收文件...") }
    var fileType by remember { mutableStateOf(getSavedFileType(context)) } // 获取上次保存的文件类型
    var inputType by remember { mutableStateOf("") } // 用户输入的文件类型

    // 检查 Intent 的 Action 是否是 VIEW（即从其他 App 打开文件）
    if (Intent.ACTION_VIEW == intent.action) {
        // 获取文件的 Uri
        val fileUri = intent.data
        if (fileUri != null) {
            // 检查是否有写入外部存储的权限
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                // 如果没有权限，请求权限
                ActivityCompat.requestPermissions(context as ComponentActivity,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            } else {
                // 如果有权限，直接保存文件
                saveFileToLocal(context, fileUri, fileType) { success ->
                    message = if (success) "文件保存成功" else "文件保存失败"
                }
            }
        }
    }

    // 显示 UI
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 显示图片的框框
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.image_123), // 加载 res/drawable 目录下的 image_123.jpg.png
                    contentDescription = "App 自带图片",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = message, style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // 输入文件类型
            TextField(
                value = inputType,
                onValueChange = { inputType = it },
                label = { Text("输入保存类型（默认 mp4）") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 保存文件类型按钮
            Button(onClick = {
                if (inputType.isNotEmpty()) {
                    fileType = inputType
                    saveFileType(context, fileType) // 保存文件类型
                    message = "保存类型已修改为：$fileType"
                }
            }) {
                Text("确定")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 一键清空按钮
            Button(onClick = {
                clearFiles(context) { success ->
                    message = if (success) "已清空所有文件" else "清空文件失败"
                }
            }) {
                Text("一键清空")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 复制路径按钮
            Button(onClick = {
                val folderPath = File(Environment.getExternalStorageDirectory(), "百度网盘保存").absolutePath
                copyToClipboard(context, folderPath)
                message = "路径已复制到剪贴板"
            }) {
                Text("复制保存路径")
            }
        }
    }
}

// 将文件保存到本地
@SuppressLint("Range")
private fun saveFileToLocal(context: android.content.Context, fileUri: Uri, fileType: String, onResult: (Boolean) -> Unit) {
    try {
        // 创建文件夹路径
        val folder = File(Environment.getExternalStorageDirectory(), "百度网盘保存")
        if (!folder.exists()) {
            folder.mkdirs() // 如果文件夹不存在，创建它
        }

        // 获取原始文件名
        val fileName = context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            } else {
                "downloaded_file"
            }
        } ?: "downloaded_file"

        // 获取文件名（不含后缀）
        val fileNameWithoutExtension = fileName.substringBeforeLast(".") // 获取文件名（不含后缀）

        // 创建输出文件
        val outputFile = File(folder, "$fileNameWithoutExtension.$fileType") // 直接修改后缀名

        // 通过 ContentResolver 打开文件的输入流
        val inputStream: InputStream? = context.contentResolver.openInputStream(fileUri)
        // 创建输出流
        val outputStream = FileOutputStream(outputFile)

        // 定义缓冲区
        val buffer = ByteArray(1024)

        // 读取输入流并写入输出流
        while (true) {
            val readLength = inputStream?.read(buffer) // 读取数据
            if (readLength == null || readLength <= 0) break // 如果读取失败或结束，退出循环
            outputStream.write(buffer, 0, readLength) // 写入数据
        }

        // 关闭输入流和输出流
        inputStream?.close()
        outputStream.close()

        // 返回保存成功
        onResult(true)
    } catch (e: IOException) {
        e.printStackTrace()
        // 返回保存失败
        onResult(false)
    }
}

// 清空文件夹中的所有文件
private fun clearFiles(context: android.content.Context, onResult: (Boolean) -> Unit) {
    try {
        val folder = File(Environment.getExternalStorageDirectory(), "百度网盘保存")
        if (folder.exists() && folder.isDirectory) {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
            onResult(true)
        } else {
            onResult(false)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(false)
    }
}

// 保存文件类型到 SharedPreferences
private fun saveFileType(context: android.content.Context, fileType: String) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppSettings", MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        putString("fileType", fileType)
        apply()
    }
}

// 从 SharedPreferences 获取保存的文件类型
private fun getSavedFileType(context: android.content.Context): String {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppSettings", MODE_PRIVATE)
    return sharedPreferences.getString("fileType", "mp4") ?: "mp4" // 默认类型为 mp4
}

// 复制路径到剪贴板
private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("保存路径", text)
    clipboard.setPrimaryClip(clip)
}
