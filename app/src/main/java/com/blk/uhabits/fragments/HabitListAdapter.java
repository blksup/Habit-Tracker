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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.colorpicker.ColorPickerDialog;
import com.android.colorpicker.ColorPickerSwatch;
import com.blk.helpers.ColorHelper;
import com.blk.helpers.DateHelper;
import com.blk.helpers.DialogHelper;
import com.blk.helpers.ReplayableActivity;
import com.blk.uhabits.R;
import com.blk.uhabits.ShowHabitActivity;
import com.blk.uhabits.commands.ArchiveHabitsCommand;
import com.blk.uhabits.commands.ChangeHabitColorCommand;
import com.blk.uhabits.commands.DeleteHabitsCommand;
import com.blk.uhabits.commands.UnarchiveHabitsCommand;
import com.blk.uhabits.helpers.ListHabitsHelper;
import com.blk.uhabits.io.CSVExporter;
import com.blk.uhabits.loaders.HabitListLoader;
import com.blk.uhabits.models.Habit;
import com.blk.uhabits.models.Score;
import com.blk.uhabits.widgets.CardBackView;
import com.blk.uhabits.widgets.SwipeDismissTouchListener;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

class HabitListAdapter extends RecyclerView.Adapter<HabitListAdapter.HabitViewHolder> {
    private LayoutInflater inflater;
    private HabitListLoader loader;
    private ListHabitsHelper helper;
    private List selectedPositions;
    private View.OnLongClickListener onCheckmarkLongClickListener;
    private View.OnClickListener onCheckmarkClickListener;
    private DialogHelper.OnSavedListener onSavedListener;

    private long mLastClickedTime;
    private ReplayableActivity activity;
    private ProgressBar progressBar;

    public HabitListAdapter(ReplayableActivity activity, HabitListLoader loader)
    {
        this.loader = loader;
        this.activity = activity;
        inflater = LayoutInflater.from(activity);
        helper = new ListHabitsHelper(activity, loader);
    }

    public void setOnSavedListener(DialogHelper.OnSavedListener onSavedListener) {
        this.onSavedListener = onSavedListener;
    }

    public Habit getItem(int position)
    {
        return loader.habitsList.get(position);
    }

    @Override
    public HabitViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new HabitViewHolder(inflater, parent);
    }

    @Override
    public void onBindViewHolder(final HabitViewHolder holder, final int position) {
        final Habit habit = loader.habitsList.get(position);
        helper.updateNameAndIcon(habit, holder.tvStar, holder.tvName, holder.tvDescription);
        helper.updateCheckmarkButtons(habit, holder.llButtons);
        helper.updateFrequencyText(habit, holder.tvFreq);
        helper.updateAction(habit, holder.tvActionArchieved, holder.tvActionEdit);

        String reminder = "None";
        if(habit.reminderHour != null || habit.reminderMin != null)
            reminder = String.format("%02d:%02d", habit.reminderHour.intValue(), habit.reminderMin.intValue());
        holder.tvReminder.setText(reminder);
        holder.tvScore.setText("Score: " + String.format("%02.0f%%", (float) habit.scores.getTodayValue() / Score.MAX_VALUE * 100));

        boolean selected = selectedPositions.contains(position);
        helper.updateHabitBackground(holder.llInner, selected);
        holder.llInner.setTag(R.string.habit_key, habit.getId());

        holder.tvActionEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditHabitFragment frag = EditHabitFragment.editSingleHabitFragment(habit.getId());
                frag.setOnSavedListener(onSavedListener);
                frag.show(activity.getFragmentManager(), "editHabit");
            }
        });

        holder.tvActionArchieved.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(habit.isArchived()){
                    activity.executeCommand(new UnarchiveHabitsCommand(habit), null);
                } else {
                    activity.executeCommand(new ArchiveHabitsCommand(habit), null);
                }
                //notifyItemChanged(position);
                notifyDataSetChanged();
            }
        });

        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, ShowHabitActivity.class);
                intent.setData(Uri.parse("content://com.blk.uhabits/habit/" + habit.getId()));
                activity.startActivity(intent);
            }
        });

        final LinkedList<Habit> selectedHabits = new LinkedList<>();
        selectedHabits.add(habit);

        SwipeDismissTouchListener swipeListener = new SwipeDismissTouchListener(holder.cardView, holder.cardView, new SwipeDismissTouchListener.DismissCallbacks() {
            @Override
            public boolean canDismiss() {
                return true;
            }

            @Override
            public void onDismiss() {
                new AlertDialog.Builder(activity).setTitle(com.blk.uhabits.R.string.delete_habits)
                        .setMessage(com.blk.uhabits.R.string.delete_habits_message)
                        .setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        activity.executeCommand(new DeleteHabitsCommand(selectedHabits), null);
                                        notifyItemRemoved(position);
                                    }
                                })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                holder.cardView.animate()
                                        .translationX(0)
                                        .alpha(1)
                                        .setDuration(activity.getResources().getInteger(android.R.integer.config_shortAnimTime))
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                holder.cardView.setPressed(false);
                                            }
                                        });
                            }
                        })
                        .show();
            }

            @Override
            public void onClick() {

            }

            @Override
            public void onLongPress() {

            }

            @Override
            public void onCancel() {

            }
        });

        swipeListener.setSupportClick(false);

        swipeListener.setSwipeCallbacks(new SwipeDismissTouchListener.SwipeCallbacks() {

            @Override
            public int onSwipeFrom(boolean right) {
                if (right) {

                    holder.cardBackView.setVisibility(View.INVISIBLE);
                    return 0;

                } else {

                    holder.cardBackView.ensureCardBackView();
                    holder.cardBackView.setBackgroundColor(habit.color);

                    int bitmask = 0;

                    bitmask |= CardBackView.FLAG_EDIT_COLOR;

                    //bitmask |= CardBackView.FLAG_EDIT;

                    //bitmask |= CardBackView.FLAG_SHARE;

                    bitmask |= CardBackView.FLAG_EXPORT_DATA;

                    holder.cardBackView.setColorListener(new View.OnClickListener() {

                        @Override

                        public void onClick(View v) {

                            if (!checkLastClickedTime()) return;

                            ColorPickerDialog picker = ColorPickerDialog.newInstance(com.blk.uhabits.R.string.color_picker_default_title, ColorHelper.palette, habit.color, 4, ColorPickerDialog.SIZE_SMALL);

                            picker.setOnColorSelectedListener(new ColorPickerSwatch.OnColorSelectedListener() {
                                public void onColorSelected(int color) {
                                    activity.executeCommand(new ChangeHabitColorCommand(selectedHabits, color), null);
                                }
                            });
                            picker.show(activity.getFragmentManager(), "picker");

                            if (SwipeDismissTouchListener.getWaitingListener() != null && SwipeDismissTouchListener.getWaitingListener().isOpened()) {
                                SwipeDismissTouchListener.getWaitingListener().cancelSwipe();
                            }

                        }

                    });

                    holder.cardBackView.setExportListener(new View.OnClickListener() {

                        @Override

                        public void onClick(View v) {

                            if (!checkLastClickedTime()) return;

                            new AsyncTask<Void, Void, Void>() {
                                String filename;
                                @Override
                                protected void onPreExecute() {
                                    if (progressBar != null) {
                                        progressBar.setIndeterminate(true);
                                        progressBar.setVisibility(View.VISIBLE);
                                    }
                                }

                                @Override
                                protected void onPostExecute(Void aVoid) {
                                    if (filename != null) {
                                        Intent intent = new Intent();
                                        intent.setAction(Intent.ACTION_SEND);
                                        intent.setType("application/zip");
                                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filename)));
                                        activity.startActivity(intent);
                                    }

                                    if (progressBar != null)
                                        progressBar.setVisibility(View.GONE);
                                }

                                @Override
                                protected Void doInBackground(Void... params) {
                                    CSVExporter exporter = new CSVExporter(activity, selectedHabits);
                                    filename = exporter.writeArchive();
                                    return null;
                                }
                            }.execute();

                            if (SwipeDismissTouchListener.getWaitingListener() != null && SwipeDismissTouchListener.getWaitingListener().isOpened()) {
                                SwipeDismissTouchListener.getWaitingListener().cancelSwipe();
                            }

                        }

                    });

                    int result = holder.cardBackView.requestLayout(bitmask);
                    if (result != 0) {
                        holder.cardBackView.setVisibility(View.VISIBLE);
                    }

                    return result;
                }
            }


            @Override
            public void onSetAlpha(float alpha) {
                holder.cardBackView.setAlpha(alpha);
            }


            @Override
            public void onSwipeFinish() {

            }

        });

        holder.cardView.setOnTouchListener(swipeListener);
    }

    @Override
    public long getItemId(int position) {
        return (getItem(position)).getId();
    }

    @Override
    public int getItemCount() {
        return loader.habits.size();
    }

    public void setSelectedPositions(List selectedPositions)
    {
        this.selectedPositions = selectedPositions;
    }

    public void setOnCheckmarkLongClickListener(View.OnLongClickListener listener) {
        this.onCheckmarkLongClickListener = listener;
    }

    public void setOnCheckmarkClickListener(View.OnClickListener listener)
    {
        this.onCheckmarkClickListener = listener;
    }

    private boolean checkLastClickedTime() {

        long current = System.currentTimeMillis();
        long diff = Math.abs(current - mLastClickedTime);
        if (diff < 500) return false;
        mLastClickedTime = System.currentTimeMillis();
        return true;

    }

    public void setProgressBar(ProgressBar progressBar) {
        this.progressBar = progressBar;
    }

    public class HabitViewHolder extends ViewHolder {

        TextView tvStar;
        TextView tvName;
        TextView tvDescription;
        TextView tvReminder;
        TextView tvFreq;
        TextView tvScore;

        TextView tvActionEdit;
        TextView tvActionArchieved;

        LinearLayout llInner;
        LinearLayout llButtons;
        CardView cardView;
        ViewStub cardBackViewStub;
        CardBackView cardBackView;

        public HabitViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.list_habits_item, parent, false));

            helper.initializeLabelAndIcon(itemView);
            helper.inflateCheckmarkButtons(itemView, onCheckmarkLongClickListener, onCheckmarkClickListener, inflater);

            tvStar = ((TextView) itemView.findViewById(R.id.tvStar));
            tvName = (TextView) itemView.findViewById(R.id.label);
            tvDescription = (TextView) itemView.findViewById(R.id.tvDescription);
            tvReminder = (TextView) itemView.findViewById(R.id.tvReminder);
            tvFreq = (TextView) itemView.findViewById(R.id.tvFreq);
            tvScore = (TextView) itemView.findViewById(R.id.tvScore);
            tvActionEdit = (TextView) itemView.findViewById(R.id.action_edit_habit);
            tvActionArchieved = (TextView) itemView.findViewById(R.id.action_archive_habit);

            llInner = (LinearLayout) itemView.findViewById(R.id.llInner);
            llButtons = (LinearLayout) itemView.findViewById(R.id.llButtons);

            cardBackViewStub = (ViewStub)itemView.findViewById(R.id.card_back_view_stub);
            cardView = (CardView) itemView.findViewById(R.id.card_view);

            cardBackView = new CardBackView(cardBackViewStub);
        }
    }
}
