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

package com.blk.uhabits.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.blk.uhabits.commands.ToggleRepetitionCommand;
import com.blk.uhabits.models.Habit;

import com.blk.uhabits.commands.Command;
import com.blk.helpers.DateHelper;
import com.blk.helpers.DialogHelper;
import com.blk.helpers.DialogHelper.OnSavedListener;
import com.blk.helpers.ReplayableActivity;
import com.blk.uhabits.R;
import com.blk.uhabits.dialogs.HabitSelectionCallback;
import com.blk.uhabits.dialogs.HintManager;
import com.blk.uhabits.helpers.ListHabitsHelper;
import com.blk.uhabits.helpers.ReminderHelper;
import com.blk.uhabits.loaders.HabitListLoader;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class ListHabitsFragment extends Fragment
        implements OnSavedListener, OnItemClickListener, OnLongClickListener,
        OnClickListener, HabitListLoader.Listener, AdapterView.OnItemLongClickListener,
        HabitSelectionCallback.Listener
{
    long lastLongClick = 0;
    private boolean isShortToggleEnabled;
    private boolean showArchived;

    private ActionMode actionMode;
    private HabitListAdapter adapter;
    private HabitListLoader loader;
    private HintManager hintManager;
    private ListHabitsHelper helper;
    private List<Integer> selectedPositions;
    private OnHabitClickListener habitClickListener;
    private ReplayableActivity activity;
    private SharedPreferences prefs;

    private RecyclerView listView;
    private LinearLayout llButtonsHeader;
    private ProgressBar progressBar;
    private View llEmpty;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list_habits_fragment, container, false);

        FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);
        fab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EditHabitFragment frag = EditHabitFragment.createHabitFragment();
                frag.setOnSavedListener(ListHabitsFragment.this);
                frag.show(getFragmentManager(), "editHabit");
            }
        });

        final AdView mAdView = (AdView)view.findViewById(R.id.adView);
        //AdRequest adRequest = new AdRequest.Builder().addTestDevice("05082F994035031C2ACD82A953CDEEE4").build();
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        mAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                mAdView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                super.onAdFailedToLoad(errorCode);
                mAdView.setVisibility(View.GONE);
            }

            @Override
            public void onAdClosed() {
                super.onAdClosed();
                mAdView.setVisibility(View.GONE);
            }
        });

        mAdView.setVisibility(View.GONE);

        View llHint = view.findViewById(R.id.llHint);
        TextView tvStarEmpty = (TextView) view.findViewById(R.id.tvStarEmpty);
        listView = (RecyclerView) view.findViewById(R.id.recyclerView);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        listView.setLayoutManager(linearLayoutManager);
        listView.setHasFixedSize(true);

        llButtonsHeader = (LinearLayout) view.findViewById(R.id.llButtonsHeader);
        llEmpty = view.findViewById(R.id.llEmpty);
        progressBar = (ProgressBar) view.findViewById(R.id.progressBar);

        selectedPositions = new LinkedList<>();
        loader = new HabitListLoader();
        helper = new ListHabitsHelper(activity, loader);
        hintManager = new HintManager(activity, llHint);

        loader.setListener(this);
        loader.setCheckmarkCount(helper.getButtonCount());
        loader.setProgressBar(progressBar);

        llHint.setOnClickListener(this);
        tvStarEmpty.setTypeface(helper.getFontawesome());

        adapter = new HabitListAdapter(activity, loader);
        adapter.setSelectedPositions(selectedPositions);
        adapter.setOnCheckmarkClickListener(this);
        adapter.setOnCheckmarkLongClickListener(this);
        adapter.setProgressBar(progressBar);
        adapter.setOnSavedListener(this);

        listView.setAdapter(adapter);

        if(savedInstanceState != null) {
            EditHabitFragment frag = (EditHabitFragment) getFragmentManager().findFragmentByTag("editHabit");
            if(frag != null) frag.setOnSavedListener(this);
        }

        loader.updateAllHabits(true);

        setHasOptionsMenu(true);
        return view;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        this.activity = (ReplayableActivity) activity;

        habitClickListener = (OnHabitClickListener) activity;
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Long timestamp = loader.getLastLoadTimestamp();

        if (timestamp != null && timestamp != DateHelper.getStartOfToday())
            loader.updateAllHabits(true);

        helper.updateEmptyMessage(llEmpty);
        helper.updateHeader(llButtonsHeader);
        hintManager.showHintIfAppropriate();

        adapter.notifyDataSetChanged();
        isShortToggleEnabled = prefs.getBoolean("pref_short_toggle", false);
    }

    @Override
    public void onLoadFinished()
    {
        adapter.notifyDataSetChanged();
        helper.updateEmptyMessage(llEmpty);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        MenuItem showArchivedItem = menu.findItem(R.id.action_show_archived);
        showArchivedItem.setChecked(showArchived);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        getActivity().getMenuInflater().inflate(R.menu.list_habits_context, menu);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final Habit habit = loader.habits.get(info.id);

        if (habit.isArchived()) menu.findItem(R.id.action_archive_habit).setVisible(false);
        else menu.findItem(R.id.action_unarchive_habit).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_show_archived:
            {
                showArchived = !showArchived;
                loader.setIncludeArchived(showArchived);
                loader.updateAllHabits(true);
                activity.invalidateOptionsMenu();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id)
    {
        if (new Date().getTime() - lastLongClick < 1000) return;

        if(actionMode == null) {
            Habit habit = loader.habitsList.get(position);
            habitClickListener.onHabitClicked(habit);
        }
        else
        {
            int k = selectedPositions.indexOf(position);
            if(k < 0)
                selectedPositions.add(position);
            else
                selectedPositions.remove(k);

            if(selectedPositions.isEmpty()) actionMode.finish();
            else actionMode.invalidate();

            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
    {
        selectItem(position);
        return true;
    }

    private void selectItem(int position) {
        if(!selectedPositions.contains(position))
            selectedPositions.add(position);

        adapter.notifyDataSetChanged();

        if(actionMode == null) {
            HabitSelectionCallback callback = new HabitSelectionCallback(activity, loader);
            callback.setSelectedPositions(selectedPositions);
            callback.setProgressBar(progressBar);
            callback.setOnSavedListener(this);
            callback.setListener(this);

            actionMode = getActivity().startActionMode(callback);
        }

        if(actionMode != null) actionMode.invalidate();
    }

    @Override
    public void onSaved(Command command, Object savedObject) {
        Habit h = (Habit) savedObject;

        if (h == null) activity.executeCommand(command, null);
        else activity.executeCommand(command, h.getId());
        adapter.notifyDataSetChanged();

        ReminderHelper.createReminderAlarms(activity);

        if(actionMode != null) actionMode.finish();
    }

    @Override
    public boolean onLongClick(View v) {
        lastLongClick = new Date().getTime();

        switch (v.getId())
        {
            case R.id.tvCheck:
                onCheckmarkLongClick(v);
                return true;
        }

        return false;
    }

    private void onCheckmarkLongClick(View v) {
        if (isShortToggleEnabled) return;

        toggleCheck(v);
        DialogHelper.vibrate(activity, 100);
    }

    private void toggleCheck(View v) {
        Long tag = (Long) v.getTag(R.string.habit_key);
        Integer offset = (Integer) v.getTag(R.string.offset_key);
        long timestamp = DateHelper.getStartOfDay(
                DateHelper.getLocalTime() - offset * DateHelper.millisecondsInOneDay);

        Habit habit = loader.habits.get(tag);
        if(habit == null) return;

        helper.toggleCheckmarkView(v, habit);
        executeCommand(new ToggleRepetitionCommand(habit, timestamp), habit.getId());
    }

    private void executeCommand(Command c, Long refreshKey) {
        activity.executeCommand(c, refreshKey);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.tvCheck:
                if (isShortToggleEnabled) toggleCheck(v);
                else activity.showToast(R.string.long_press_to_toggle);
                break;

            case R.id.llHint:
                hintManager.dismissHint();
                break;
        }
    }

    public void onPostExecuteCommand(Long refreshKey) {
        if (refreshKey == null) loader.updateAllHabits(true);
        else loader.updateHabit(refreshKey);
    }

    public void onActionModeDestroyed(ActionMode mode) {
        actionMode = null;
        selectedPositions.clear();
        adapter.notifyDataSetChanged();
    }

    public interface OnHabitClickListener
    {
        void onHabitClicked(Habit habit);
    }
}
