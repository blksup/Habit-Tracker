/*
 * Copyright (C) 2016 Álinson Santos Xavier <isoron@gmail.com>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.blk.uhabits;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import android.support.v7.app.ActionBar;
import com.blk.helpers.ReplayableActivity;
import com.blk.uhabits.fragments.ShowHabitFragment;
import com.blk.uhabits.models.Habit;

public class ShowHabitActivity extends ReplayableActivity
{

    public Habit habit;
    private Receiver receiver;
    private LocalBroadcastManager localBroadcastManager;

    private ShowHabitFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Uri data = getIntent().getData();
        habit = Habit.get(ContentUris.parseId(data));
        ActionBar actionBar = getSupportActionBar();

        if(actionBar != null) {
            actionBar.setTitle(habit.name);
        }

        setContentView(R.layout.show_habit_activity);

        fragment = (ShowHabitFragment) getFragmentManager().findFragmentById(R.id.fragment2);

        receiver = new Receiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(receiver,
                new IntentFilter(MainActivity.ACTION_REFRESH));
    }

    class Receiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            fragment.refreshData();
        }
    }

    @Override
    protected void onDestroy()
    {
        localBroadcastManager.unregisterReceiver(receiver);
        super.onDestroy();
    }
}
