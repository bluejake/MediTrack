package com.medilog.app.util

import android.content.Context
import com.medilog.app.R
import com.medilog.app.data.MedRepository
import com.medilog.app.model.DoseEvent
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule
import java.util.Random

/** 示例数据：便于首次启动直接体验完整功能 */
object DemoData {

    fun load(ctx: Context) {
        val repo = MedRepository(ctx)
        val forms = Palette.forms(ctx)

        // 1. 二甲双胍片（每日 2 次）
        val m1 = Medication()
        m1.name = ctx.getString(R.string.demo_med1_name)
        m1.form = forms[0]
        m1.strength = ctx.getString(R.string.demo_med1_strength)
        m1.icon = 0
        m1.color = 0
        m1.asNeeded = false
        m1.startDate = TimeUtils.shiftDay(TimeUtils.today(), -60)
        m1.durationDays = 0
        m1.remind = true
        m1.notes = ctx.getString(R.string.demo_med1_notes)
        val id1 = repo.insertMedication(m1, listOf(mk(8, 0), mk(20, 0)))
        val sc1a = repo.schedulesOf(id1)[0]
        val sc1b = repo.schedulesOf(id1)[1]

        // 2. 阿托伐他汀钙片（每晚 1 次）
        val m2 = Medication()
        m2.name = ctx.getString(R.string.demo_med2_name)
        m2.form = forms[0]
        m2.strength = ctx.getString(R.string.demo_med2_strength)
        m2.icon = 0
        m2.color = 1
        m2.asNeeded = false
        m2.startDate = TimeUtils.shiftDay(TimeUtils.today(), -30)
        m2.durationDays = 0
        m2.remind = true
        m2.notes = ctx.getString(R.string.demo_med2_notes)
        val id2 = repo.insertMedication(m2, listOf(mk(21, 0)))
        val sc2 = repo.schedulesOf(id2)[0]

        // 3. 维生素 C 片（每日 1 次）
        val m3 = Medication()
        m3.name = ctx.getString(R.string.demo_med3_name)
        m3.form = forms[0]
        m3.strength = ctx.getString(R.string.demo_med3_strength)
        m3.icon = 0
        m3.color = 3
        m3.asNeeded = false
        m3.startDate = TimeUtils.shiftDay(TimeUtils.today(), -14)
        m3.durationDays = 0
        m3.remind = true
        val id3 = repo.insertMedication(m3, listOf(mk(8, 30)))
        val sc3 = repo.schedulesOf(id3)[0]

        // 4. 布洛芬胶囊（按需）
        val m4 = Medication()
        m4.name = ctx.getString(R.string.demo_med4_name)
        m4.form = forms[1]
        m4.strength = ctx.getString(R.string.demo_med4_strength)
        m4.icon = 1
        m4.color = 6
        m4.asNeeded = true
        m4.startDate = TimeUtils.shiftDay(TimeUtils.today(), -14)
        m4.durationDays = 0
        m4.remind = false
        m4.notes = ctx.getString(R.string.demo_med4_notes)
        repo.insertMedication(m4, ArrayList())

        // 过去 10 天随机服药记录
        val rnd = Random()
        val today = TimeUtils.today()
        for (d in 10 downTo 1) {
            val date = TimeUtils.shiftDay(today, -d.toLong())
            record(repo, id1, sc1a.id, date, "08:00", rnd)
            record(repo, id1, sc1b.id, date, "20:00", rnd)
            record(repo, id2, sc2.id, date, "21:00", rnd)
            record(repo, id3, sc3.id, date, "08:30", rnd)
        }
    }

    private fun record(
        repo: MedRepository, medId: Long, scheduleId: Long, date: String, hhmm: String, rnd: Random
    ) {
        val m = repo.getMedication(medId) ?: return
        val e = DoseEvent()
        e.med = m
        e.planDate = date
        e.planTime = hhmm
        val s = Schedule()
        s.id = scheduleId
        // 必须把时分写进桩 Schedule：takeScheduled 取 sch.hhmm() 存 plan_time，
        // 只有 id 的桩会让所有演示记录的「计划时间」显示成 00:00
        val hm = hhmm.split(":")
        s.hour = hm[0].toInt()
        s.minute = hm[1].toInt()
        e.schedule = s
        val at = TimeUtils.millisAt(date, hhmm)
        if (rnd.nextInt(100) < 85) {
            repo.takeScheduled(e, at)
        } else {
            repo.skipScheduled(e, at)
        }
    }

    private fun mk(h: Int, m: Int): Schedule {
        val s = Schedule()
        s.hour = h
        s.minute = m
        s.enabled = true
        return s
    }
}
