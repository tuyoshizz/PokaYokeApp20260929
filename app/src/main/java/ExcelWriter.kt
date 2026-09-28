package com.example.pokayokeapp

import android.content.Context
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExcelWriter {

    fun saveHistory(
        context: Context,
        parent: String,
        child: String
    ) {

        val file = File(
            context.getExternalFilesDir(null),
            "history.xlsx"
        )

        val workbook: XSSFWorkbook
        val sheet: org.apache.poi.ss.usermodel.Sheet

        if (file.exists()) {
            val fis = FileInputStream(file)
            workbook = XSSFWorkbook(fis)
            sheet = workbook.getSheetAt(0)
        } else {
            workbook = XSSFWorkbook()
            sheet = workbook.createSheet("履歴")

            // ヘッダー
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("日時")
            header.createCell(1).setCellValue("親品番")
            header.createCell(2).setCellValue("子品番")
        }

        val rowNum = sheet.lastRowNum + 1
        val row = sheet.createRow(rowNum)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        row.createCell(0).setCellValue(sdf.format(Date()))
        row.createCell(1).setCellValue(parent)
        row.createCell(2).setCellValue(child)

        val fos = FileOutputStream(file)
        workbook.write(fos)

        fos.close()
        workbook.close()
    }
}