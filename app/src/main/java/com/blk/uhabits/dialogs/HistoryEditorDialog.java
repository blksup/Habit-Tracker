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

package com.blk.uhabits.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

import com.blk.uhabits.models.Habit;
import com.blk.uhabits.R;
import com.blk.uhabits.views.HabitHistoryView;

public class HistoryEditorDialog extends DialogFragment
    implements DialogInterface.OnClickListener
{
    private Habit habit;
    private Listener listener;
    HabitHistoryView historyView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Context context = getActivity();
        historyView = new HabitHistoryView(context, null);
        int p = (int) getResources().getDimension(R.dimen.history_editor_padding);

        if(savedInstanceState != null)
        {
            long id = savedInstanceState.getLong("habit", -1);
            if(id > 0) this.habit = Habit.get(id);
        }

        historyView.setPadding(p, 0, p, 0);
        historyView.setHabit(habit);
        historyView.setIsEditable(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.history)
                .setView(historyView)
                .setPositiveButton(android.R.string.ok, this);

        return builder.create();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.history_editor_max_height);
        int width = metrics.widthPixels;
        int height = Math.min(metrics.heightPixels, maxHeight);

        Log.d("HistoryEditorDialog", String.format("h=%d max_h=%d", height, maxHeight));

        getDialog().getWindow().setLayout(width, height);
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        dismiss();
    }

    public void setHabit(Habit habit)
    {
        this.habit = habit;
        if(historyView != null) historyView.setHabit(habit);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if(listener != null) listener.onHistoryEditorClosed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putLong("habit", habit.getId());
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public interface Listener {
        void onHistoryEditorClosed();
    }
}
