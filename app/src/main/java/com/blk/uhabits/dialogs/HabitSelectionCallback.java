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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.android.colorpicker.ColorPickerDialog;
import com.android.colorpicker.ColorPickerSwatch;

import com.blk.helpers.ColorHelper;
import com.blk.helpers.DialogHelper;
import com.blk.helpers.ReplayableActivity;
import com.blk.uhabits.commands.ArchiveHabitsCommand;
import com.blk.uhabits.commands.DeleteHabitsCommand;
import com.blk.uhabits.commands.UnarchiveHabitsCommand;
import com.blk.uhabits.fragments.EditHabitFragment;
import com.blk.uhabits.io.CSVExporter;
import com.blk.uhabits.loaders.HabitListLoader;
import com.blk.uhabits.models.Habit;
import com.blk.uhabits.commands.ChangeHabitColorCommand;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class HabitSelectionCallback implements ActionMode.Callback
{
    private HabitListLoader loader;
    private List<Integer> selectedPositions;
    private ReplayableActivity activity;
    private Listener listener;
    private DialogHelper.OnSavedListener onSavedListener;
    private ProgressBar progressBar;

    public interface Listener
    {
        void onActionModeDestroyed(ActionMode mode);
    }

    public HabitSelectionCallback(ReplayableActivity activity, HabitListLoader loader)
    {
        this.activity = activity;
        this.loader = loader;
        selectedPositions = new LinkedList<>();
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public void setProgressBar(ProgressBar progressBar)
    {
        this.progressBar = progressBar;
    }

    public void setOnSavedListener(DialogHelper.OnSavedListener onSavedListener)
    {
        this.onSavedListener = onSavedListener;
    }

    public void setSelectedPositions(List<Integer> selectedPositions)
    {
        this.selectedPositions = selectedPositions;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu)
    {
        activity.getMenuInflater().inflate(com.blk.uhabits.R.menu.list_habits_context, menu);
        updateTitle(mode);
        updateActions(menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu)
    {
        updateTitle(mode);
        updateActions(menu);
        return true;
    }

    private void updateActions(Menu menu)
    {
        boolean showEdit = (selectedPositions.size() == 1);
        boolean showArchive = true;
        boolean showUnarchive = true;
        for (int i : selectedPositions)
        {
            Habit h = loader.habitsList.get(i);
            if (h.isArchived())
            {
                showArchive = false;
            }
            else showUnarchive = false;
        }

        MenuItem itemEdit = menu.findItem(com.blk.uhabits.R.id.action_edit_habit);
        MenuItem itemColor = menu.findItem(com.blk.uhabits.R.id.action_color);
        MenuItem itemArchive = menu.findItem(com.blk.uhabits.R.id.action_archive_habit);
        MenuItem itemUnarchive = menu.findItem(com.blk.uhabits.R.id.action_unarchive_habit);

        itemColor.setVisible(true);
        itemEdit.setVisible(showEdit);
        itemArchive.setVisible(showArchive);
        itemUnarchive.setVisible(showUnarchive);
    }

    private void updateTitle(ActionMode mode)
    {
        mode.setTitle("" + selectedPositions.size());
    }

    @Override
    public boolean onActionItemClicked(final ActionMode mode, MenuItem item)
    {
        final LinkedList<Habit> selectedHabits = new LinkedList<>();
        for (int i : selectedPositions)
            selectedHabits.add(loader.habitsList.get(i));

        Habit firstHabit = selectedHabits.getFirst();

        switch (item.getItemId())
        {
            case com.blk.uhabits.R.id.action_archive_habit:
                activity.executeCommand(new ArchiveHabitsCommand(selectedHabits), null);
                mode.finish();
                return true;

            case com.blk.uhabits.R.id.action_unarchive_habit:
                activity.executeCommand(new UnarchiveHabitsCommand(selectedHabits), null);
                mode.finish();
                return true;

            case com.blk.uhabits.R.id.action_edit_habit:
            {
                EditHabitFragment frag = EditHabitFragment.editSingleHabitFragment(firstHabit.getId());
                frag.setOnSavedListener(onSavedListener);
                frag.show(activity.getFragmentManager(), "editHabit");
                return true;
            }

            case com.blk.uhabits.R.id.action_color:
            {
                ColorPickerDialog picker = ColorPickerDialog.newInstance(com.blk.uhabits.R.string.color_picker_default_title,
                        ColorHelper.palette, firstHabit.color, 4, ColorPickerDialog.SIZE_SMALL);

                picker.setOnColorSelectedListener(new ColorPickerSwatch.OnColorSelectedListener()
                        {
                            public void onColorSelected(int color)
                            {
                                activity.executeCommand(
                                        new ChangeHabitColorCommand(selectedHabits, color), null);
                                mode.finish();
                            }
                        });
                picker.show(activity.getFragmentManager(), "picker");
                return true;
            }

            case com.blk.uhabits.R.id.action_delete:
            {
                new AlertDialog.Builder(activity).setTitle(com.blk.uhabits.R.string.delete_habits)
                        .setMessage(com.blk.uhabits.R.string.delete_habits_message)
                        .setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        activity.executeCommand(
                                                new DeleteHabitsCommand(selectedHabits), null);
                                        mode.finish();
                                    }
                                }).setNegativeButton(android.R.string.no, null)
                        .show();

                return true;
            }

            case com.blk.uhabits.R.id.action_export_csv:
            {
                onExportHabitsClick(selectedHabits);
                return true;
            }
        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode)
    {
        if(listener != null) listener.onActionModeDestroyed(mode);
    }

    private void onExportHabitsClick(final LinkedList<Habit> selectedHabits)
    {
        new AsyncTask<Void, Void, Void>()
        {
            String filename;

            @Override
            protected void onPreExecute()
            {
                if(progressBar != null)
                {
                    progressBar.setIndeterminate(true);
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            protected void onPostExecute(Void aVoid)
            {
                if(filename != null)
                {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filename)));

                    activity.startActivity(intent);
                }

                if(progressBar != null)
                    progressBar.setVisibility(View.GONE);
            }

            @Override
            protected Void doInBackground(Void... params)
            {
                CSVExporter exporter = new CSVExporter(activity, selectedHabits);
                filename = exporter.writeArchive();
                return null;
            }
        }.execute();
    }
}
