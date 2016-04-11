package com.wefika.calendar;

import android.app.Activity;
import android.os.Bundle;

import com.blk.uhabits.R;
import com.wefika.calendar.CollapseCalendarView;
import com.wefika.calendar.manager.CalendarManager;

import org.joda.time.LocalDate;

public class MainActivity extends Activity {

    private CollapseCalendarView mCalendarView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CalendarManager manager = new CalendarManager(LocalDate.now(), CalendarManager.State.WEEK, LocalDate.now(), LocalDate.now().plusYears(1));

        manager.selectDay(LocalDate.now().plusDays(1));
        manager.selectDay(LocalDate.now().plusDays(2));
        manager.selectDay(LocalDate.now().plusDays(3));

        mCalendarView = (CollapseCalendarView) findViewById(R.id.calendar);
        mCalendarView.init(manager);
    }
}
