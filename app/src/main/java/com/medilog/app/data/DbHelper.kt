package com.medilog.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VER) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE " + T_MED + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "form TEXT,"
                + "strength TEXT,"
                + "icon INTEGER DEFAULT 0,"
                + "color INTEGER DEFAULT 0,"
                + "as_needed INTEGER DEFAULT 0,"
                + "start_date TEXT,"
                + "duration_days INTEGER DEFAULT 0,"
                + "remind INTEGER DEFAULT 1,"
                + "notes TEXT,"
                + "created_at INTEGER DEFAULT 0)"
        )

        db.execSQL(
            "CREATE TABLE " + T_SCHED + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "med_id INTEGER NOT NULL,"
                + "hour INTEGER,"
                + "minute INTEGER,"
                + "enabled INTEGER DEFAULT 1)"
        )

        db.execSQL(
            "CREATE TABLE " + T_LOG + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "med_id INTEGER NOT NULL,"
                + "schedule_id INTEGER DEFAULT 0,"
                + "plan_date TEXT NOT NULL,"
                + "plan_time TEXT,"
                + "actual_at INTEGER DEFAULT 0,"
                + "status TEXT)"
        )

        db.execSQL("CREATE INDEX idx_sched_med ON " + T_SCHED + "(med_id)")
        db.execSQL("CREATE INDEX idx_log_date ON " + T_LOG + "(plan_date)")
        db.execSQL("CREATE INDEX idx_log_med ON " + T_LOG + "(med_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        // 首个版本，暂无升级逻辑
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        private const val DB_NAME = "medilog.db"
        private const val DB_VER = 1

        const val T_MED = "medications"
        const val T_SCHED = "schedules"
        const val T_LOG = "dose_logs"
    }
}
