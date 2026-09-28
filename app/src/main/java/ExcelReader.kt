package com.example.pokayokeapp

import android.content.Context
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ExcelReader {

    fun load(context: Context, parentCode: String): MutableList<ChildItem> {

        val list = mutableListOf<ChildItem>()

        val inputStream = context.assets.open("parts.xlsx")
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)

        for (i in 1..sheet.lastRowNum) {

            val row = sheet.getRow(i) ?: continue

            val parent = row.getCell(0)?.stringCellValue ?: ""
            val code = row.getCell(1)?.stringCellValue ?: ""
            val name = row.getCell(2)?.stringCellValue ?: ""
            val loc = row.getCell(3)?.stringCellValue ?: ""

            if (parent == parentCode) {
                list.add(ChildItem(code, name, loc))
            }
        }

        workbook.close()

        return list
    }
}