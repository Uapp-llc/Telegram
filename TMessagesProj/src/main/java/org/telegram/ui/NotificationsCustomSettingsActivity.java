/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static org.telegram.messenger.NotificationsController.TYPE_CATEGORY;

public class NotificationsCustomSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private EmptyTextProgressView emptyView;
    private SearchAdapter searchAdapter;
    private AnimatorSet animatorSet;

    private boolean searchWas;
    private boolean searching;

    private final static int search_button = 0;

    private int alertRow;
    private int alertSection2Row;
    private int messageSectionRow;
    private int previewRow;
    private int messageVibrateRow;
    private int messageSoundRow;
    private int messageLedRow;
    private int messagePopupNotificationRow;
    private int messagePriorityRow;
    private int groupSection2Row;
    private int exceptionsAddRow;
    private int exceptionsStartRow;
    private int exceptionsEndRow;
    private int exceptionsSection2Row;
    private int deleteAllRow;
    private int deleteAllSectionRow;
    private int rowCount = 0;

    private long categoryId;

    private int currentType;
    private ArrayList<NotificationsSettingsActivity.NotificationException> exceptions;
    private HashMap<Long, NotificationsSettingsActivity.NotificationException> exceptionsDict = new HashMap<>();

    public NotificationsCustomSettingsActivity(int type, ArrayList<NotificationsSettingsActivity.NotificationException> notificationExceptions) {
        this(type, notificationExceptions, false, -1);
    }

    public NotificationsCustomSettingsActivity(int type, ArrayList<NotificationsSettingsActivity.NotificationException> notificationExceptions, boolean load) {
        this(type, notificationExceptions, load, -1);
    }

    public NotificationsCustomSettingsActivity(int type, ArrayList<NotificationsSettingsActivity.NotificationException> notificationExceptions, boolean load, long categoryId) {
        super();
        currentType = type;
        this.categoryId = categoryId;
        exceptions = notificationExceptions;
        for (int a = 0, N = exceptions.size(); a < N; a++) {
            NotificationsSettingsActivity.NotificationException exception = exceptions.get(a);
            exceptionsDict.put(exception.did, exception);
        }
        if (load) {
            loadExceptions();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        updateRows();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == -1) {
            actionBar.setTitle(LocaleController.getString("NotificationsExceptions", R.string.NotificationsExceptions));
        } else {
            actionBar.setTitle(LocaleController.getString("Notifications", R.string.Notifications));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        if (exceptions != null && !exceptions.isEmpty() && currentType != TYPE_CATEGORY) {
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    emptyView.setShowAtCenter(true);
                }

                @Override
                public void onSearchCollapse() {
                    searchAdapter.searchDialogs(null);
                    searching = false;
                    searchWas = false;
                    emptyView.setText(LocaleController.getString("NoExceptions", R.string.NoExceptions));
                    listView.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                    listView.setFastScrollVisible(true);
                    listView.setVerticalScrollBarEnabled(false);
                    emptyView.setShowAtCenter(false);
                }

                @Override
                public void onTextChanged(EditText editText) {
                    if (searchAdapter == null) {
                        return;
                    }
                    String text = editText.getText().toString();
                    if (text.length() != 0) {
                        searchWas = true;
                        if (listView != null) {
                            emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                            emptyView.showProgress();
                            listView.setAdapter(searchAdapter);
                            searchAdapter.notifyDataSetChanged();
                            listView.setFastScrollVisible(false);
                            listView.setVerticalScrollBarEnabled(true);
                        }
                    }
                    searchAdapter.searchDialogs(text);
                }
            });
            searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        }

        searchAdapter = new SearchAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyView = new EmptyTextProgressView(context);
        emptyView.setTextSize(18);
        emptyView.setText(LocaleController.getString("NoExceptions", R.string.NoExceptions));
        emptyView.showTextView();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            boolean enabled = false;
            if (getParentActivity() == null) {
                return;
            }
            if (listView.getAdapter() == searchAdapter || position >= exceptionsStartRow && position < exceptionsEndRow) {
                ArrayList<NotificationsSettingsActivity.NotificationException> arrayList;
                NotificationsSettingsActivity.NotificationException exception;
                boolean newException;
                if (listView.getAdapter() == searchAdapter) {
                    Object object = searchAdapter.getObject(position);
                    if (object instanceof NotificationsSettingsActivity.NotificationException) {
                        arrayList = searchAdapter.searchResult;
                        exception = (NotificationsSettingsActivity.NotificationException) object;
                        newException = false;
                    } else {
                        long did;
                        if (object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) object;
                            did = user.id;
                        } else {
                            TLRPC.Chat chat = (TLRPC.Chat) object;
                            did = -chat.id;
                        }
                        if (exceptionsDict.containsKey(did)) {
                            exception = exceptionsDict.get(did);
                            newException = false;
                        } else {
                            newException = true;
                            exception = new NotificationsSettingsActivity.NotificationException();
                            exception.did = did;
                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                exception.did = user.id;
                            } else {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                exception.did = -chat.id;
                            }
                        }
                        arrayList = exceptions;
                    }
                } else {
                    arrayList = exceptions;
                    int index = position - exceptionsStartRow;
                    if (index < 0 || index >= arrayList.size()) {
                        return;
                    }
                    exception = arrayList.get(index);
                    newException = false;
                }
                if (exception == null) {
                    return;
                }

                AlertsCreator.showCustomNotificationsDialog(NotificationsCustomSettingsActivity.this, exception.did, -1, categoryId, null, currentAccount, null, param -> {
                    if (param == 0) {
                        if (newException) {
                            return;
                        }
                        if (arrayList != exceptions) {
                            int idx = exceptions.indexOf(exception);
                            if (idx >= 0) {
                                exceptions.remove(idx);
                                exceptionsDict.remove(exception.did);
                            }
                        }
                        arrayList.remove(exception);
                        if (exceptionsAddRow != -1 && arrayList.isEmpty() && arrayList == exceptions) {
                            listView.getAdapter().notifyItemChanged(exceptionsAddRow);
                            listView.getAdapter().notifyItemRemoved(deleteAllRow);
                            listView.getAdapter().notifyItemRemoved(deleteAllSectionRow);
                        }
                        listView.getAdapter().notifyItemRemoved(position);

                        updateRows();
                        checkRowsEnabled();
                        actionBar.closeSearchField();
                    } else {
                        SharedPreferences preferences = getNotificationsSettings();
                        String categoryKeyPrefix = "";
                        if (currentType == TYPE_CATEGORY) {
                            categoryKeyPrefix = "category_";
                        }
                        exception.hasCustom = preferences.getBoolean(categoryKeyPrefix + "custom_" + exception.did, false);
                        exception.notify = preferences.getInt(categoryKeyPrefix + "notify2_" + exception.did, 0);
                        if (exception.notify != 0) {
                            int time = preferences.getInt(categoryKeyPrefix + "notifyuntil_" + exception.did, -1);
                            if (time != -1) {
                                exception.muteUntil = time;
                            }
                        }
                        if (newException) {
                            exceptions.add(exception);
                            exceptionsDict.put(exception.did, exception);
                            updateRows();
                            adapter.notifyDataSetChanged();
                        } else {
                            listView.getAdapter().notifyItemChanged(position);
                        }
                        actionBar.closeSearchField();
                    }
                });
                return;
            }
            if (position == exceptionsAddRow) {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putBoolean("checkCanWrite", false);
                if (currentType == NotificationsController.TYPE_GROUP) {
                    args.putInt("dialogsType", 6);
                } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                    args.putInt("dialogsType", 5);
                } else if (currentType == NotificationsController.TYPE_PRIVATE) {
                    args.putInt("dialogsType", 4);
                } else {
                    args.putInt("dialogsType", 12);
                    args.putLong("existingCategoryId", categoryId);
                }
                DialogsActivity activity = new DialogsActivity(args);
                activity.setDelegate((fragment, dids, message, param) -> {
                    Bundle args2 = new Bundle();
                    args2.putLong("dialog_id", dids.get(0));
                    args2.putBoolean("exception", true);
                    args2.putBoolean("category", currentType == TYPE_CATEGORY);
                    ProfileNotificationsActivity profileNotificationsActivity = new ProfileNotificationsActivity(args2);
                    profileNotificationsActivity.setDelegate(exception -> {
                        int oldExceptionIdx = exceptions.indexOf(exception);
                        if (oldExceptionIdx != -1) {
                            exceptions.set(oldExceptionIdx, exception);
                        } else {
                            exceptions.add(0, exception);
                        }
                        updateRows();
                        adapter.notifyDataSetChanged();
                    });
                    presentFragment(profileNotificationsActivity, true);
                });
                presentFragment(activity);
            } else if (position == deleteAllRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("NotificationsDeleteAllExceptionTitle", R.string.NotificationsDeleteAllExceptionTitle));
                builder.setMessage(LocaleController.getString("NotificationsDeleteAllExceptionAlert", R.string.NotificationsDeleteAllExceptionAlert));
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                    SharedPreferences preferences = getNotificationsSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    String categoryKeyPrefix = "";
                    if (currentType == TYPE_CATEGORY) {
                        categoryKeyPrefix = "category_";
                    }
                    for (int a = 0, N = exceptions.size(); a < N; a++) {
                        NotificationsSettingsActivity.NotificationException exception = exceptions.get(a);
                        editor.remove(categoryKeyPrefix + "notify2_" + exception.did).remove(categoryKeyPrefix + "custom_" + exception.did);
                        getMessagesStorage().setDialogFlags(exception.did, 0);
                        TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(exception.did);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                        }
                    }
                    editor.commit();
                    if (currentType != TYPE_CATEGORY) {
                        for (int a = 0, N = exceptions.size(); a < N; a++) {
                            NotificationsSettingsActivity.NotificationException exception = exceptions.get(a);
                            getNotificationsController().updateServerNotificationsSettings(exception.did, false);
                        }
                    }

                    exceptions.clear();
                    exceptionsDict.clear();
                    updateRows();
                    getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    adapter.notifyDataSetChanged();
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            } else if (position == alertRow) {
                enabled = getNotificationsController().isGlobalNotificationsEnabled(currentType, categoryId);

                NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                if (!enabled) {
                    getNotificationsController().setGlobalNotificationsEnabled(currentType, 0, categoryId);
                    checkCell.setChecked(!enabled);
                    if (holder != null) {
                        adapter.onBindViewHolder(holder, position);
                    }
                    checkRowsEnabled();
                } else {
                    AlertsCreator.showCustomNotificationsDialog(NotificationsCustomSettingsActivity.this, 0, currentType, categoryId, exceptions, currentAccount, param -> {
                        int offUntil;
                        SharedPreferences preferences = getNotificationsSettings();
                        if (currentType == NotificationsController.TYPE_PRIVATE) {
                            offUntil = preferences.getInt("EnableAll2", 0);
                        } else if (currentType == NotificationsController.TYPE_GROUP) {
                            offUntil = preferences.getInt("EnableGroup2", 0);
                        } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                            offUntil = preferences.getInt("EnableChannel2", 0);
                        } else {
                            offUntil = preferences.getInt(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_ENABLED, categoryId), 0);
                        }
                        int currentTime = getConnectionsManager().getCurrentTime();
                        int iconType;
                        if (offUntil < currentTime) {
                            iconType = 0;
                        } else if (offUntil - 60 * 60 * 24 * 365 >= currentTime) {
                            iconType = 0;
                        } else {
                            iconType = 2;
                        }
                        checkCell.setChecked(getNotificationsController().isGlobalNotificationsEnabled(currentType, categoryId), iconType);
                        if (holder != null) {
                            adapter.onBindViewHolder(holder, position);
                        }
                        checkRowsEnabled();
                    });
                }
            } else if (position == previewRow) {
                if (!view.isEnabled()) {
                    return;
                }
                SharedPreferences preferences = getNotificationsSettings();
                SharedPreferences.Editor editor = preferences.edit();
                if (currentType == NotificationsController.TYPE_PRIVATE) {
                    enabled = preferences.getBoolean("EnablePreviewAll", true);
                    editor.putBoolean("EnablePreviewAll", !enabled);
                } else if (currentType == NotificationsController.TYPE_GROUP) {
                    enabled = preferences.getBoolean("EnablePreviewGroup", true);
                    editor.putBoolean("EnablePreviewGroup", !enabled);
                } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                    enabled = preferences.getBoolean("EnablePreviewChannel", true);
                    editor.putBoolean("EnablePreviewChannel", !enabled);
                } else {
                    enabled = preferences.getBoolean(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_PREVIEW, categoryId), true);
                    editor.putBoolean(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_PREVIEW, categoryId), !enabled);
                }
                editor.commit();
                getNotificationsController().updateServerNotificationsSettings(currentType);
            } else if (position == messageSoundRow) {
                if (!view.isEnabled()) {
                    return;
                }
                try {
                    SharedPreferences preferences = getNotificationsSettings();
                    Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    Uri currentSound = null;

                    String defaultPath = null;
                    Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                    if (defaultUri != null) {
                        defaultPath = defaultUri.getPath();
                    }

                    String path;
                    if (currentType == NotificationsController.TYPE_PRIVATE) {
                        path = preferences.getString("GlobalSoundPath", defaultPath);
                    } else if (currentType == NotificationsController.TYPE_GROUP) {
                        path = preferences.getString("GroupSoundPath", defaultPath);
                    } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                        path = preferences.getString("ChannelSoundPath", defaultPath);
                    } else {
                        path = preferences.getString(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_SOUND_PATH, categoryId), defaultPath);
                    }

                    if (path != null && !path.equals("NoSound")) {
                        if (path.equals(defaultPath)) {
                            currentSound = defaultUri;
                        } else {
                            currentSound = Uri.parse(path);
                        }
                    }

                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                    startActivityForResult(tmpIntent, position);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (position == messageLedRow) {
                if (!view.isEnabled()) {
                    return;
                }
                showDialog(AlertsCreator.createColorSelectDialog(getParentActivity(), NotificationsCustomSettingsActivity.this, 0, currentType, categoryId, () -> {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        adapter.onBindViewHolder(holder, position);
                    }
                }));
            } else if (position == messagePopupNotificationRow) {
                if (!view.isEnabled()) {
                    return;
                }
                showDialog(AlertsCreator.createPopupSelectDialog(getParentActivity(), currentType, categoryId, () -> {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        adapter.onBindViewHolder(holder, position);
                    }
                }));
            } else if (position == messageVibrateRow) {
                if (!view.isEnabled()) {
                    return;
                }
                String key;
                if (currentType == NotificationsController.TYPE_PRIVATE) {
                    key = "vibrate_messages";
                } else if (currentType == NotificationsController.TYPE_GROUP) {
                    key = "vibrate_group";
                } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                    key = "vibrate_channel";
                } else {
                    key = String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_VIBRATE, categoryId);
                }
                showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), 0, key, () -> {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        adapter.onBindViewHolder(holder, position);
                    }
                }));
            } else if (position == messagePriorityRow) {
                if (!view.isEnabled()) {
                    return;
                }
                showDialog(AlertsCreator.createPrioritySelectDialog(getParentActivity(), 0, currentType, categoryId, () -> {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        adapter.onBindViewHolder(holder, position);
                    }
                }));
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!enabled);
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });

        return fragmentView;
    }

    private void checkRowsEnabled() {
        if (!exceptions.isEmpty()) {
            return;
        }
        int count = listView.getChildCount();
        ArrayList<Animator> animators = new ArrayList<>();
        boolean enabled = getNotificationsController().isGlobalNotificationsEnabled(currentType, categoryId);
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (holder.getAdapterPosition() == messageSectionRow) {
                        headerCell.setEnabled(enabled, animators);
                    }
                    break;
                }
                case 1: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    textCell.setEnabled(enabled, animators);
                    break;
                }
                case 3: {
                    TextColorCell textCell = (TextColorCell) holder.itemView;
                    textCell.setEnabled(enabled, animators);
                    break;
                }
                case 5: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setEnabled(enabled, animators);
                    break;
                }
            }
        }
        if (!animators.isEmpty()) {
            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(animatorSet)) {
                        animatorSet = null;
                    }
                }
            });
            animatorSet.setDuration(150);
            animatorSet.start();
        }
    }

    private void loadExceptions() {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<NotificationsSettingsActivity.NotificationException> usersResult = new ArrayList<>();
            ArrayList<NotificationsSettingsActivity.NotificationException> chatsResult = new ArrayList<>();
            ArrayList<NotificationsSettingsActivity.NotificationException> channelsResult = new ArrayList<>();
            ArrayList<NotificationsSettingsActivity.NotificationException> categoryDialogsResult = new ArrayList<>();
            ArrayList<Long> categoryAllDialogs = new ArrayList<>();
            LongSparseArray<NotificationsSettingsActivity.NotificationException> waitingForLoadExceptions = new LongSparseArray<>();

            String categoryExceptionKeyPrefix = "";
            if (categoryId != -1) {
                categoryAllDialogs.addAll(getMessagesController().categories_dict.get(categoryId).getDialogs());
                categoryExceptionKeyPrefix = "category_";
            }
            ArrayList<Integer> usersToLoad = new ArrayList<>();
            ArrayList<Integer> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedChatsToLoad = new ArrayList<>();

            ArrayList<TLRPC.User> users = new ArrayList<>();
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            int selfId = getUserConfig().clientUserId;

            SharedPreferences preferences = getNotificationsSettings();
            Map<String, ?> values = preferences.getAll();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(categoryExceptionKeyPrefix + "notify2_")) {
                    key = key.replace(categoryExceptionKeyPrefix + "notify2_", "");

                    long did = Utilities.parseLong(key);
                    if (did != 0 && did != selfId) {
                        NotificationsSettingsActivity.NotificationException exception = new NotificationsSettingsActivity.NotificationException();
                        exception.did = did;
                        exception.hasCustom = preferences.getBoolean(categoryExceptionKeyPrefix + "custom_" + did, false);
                        exception.notify = (Integer) entry.getValue();
                        if (exception.notify != 0) {
                            Integer time = (Integer) values.get(categoryExceptionKeyPrefix + "notifyuntil_" + key);
                            if (time != null) {
                                exception.muteUntil = time;
                            }
                        }

                        int lower_id = (int) did;
                        int high_id = (int) (did << 32);

                        boolean isInCategory = false;
                        if (categoryAllDialogs.contains(did)) {
                            isInCategory = true;
                        }
                        if (lower_id != 0) {
                            if (lower_id > 0) {
                                TLRPC.User user = getMessagesController().getUser(lower_id);
                                if (user == null) {
                                    usersToLoad.add(lower_id);
                                    waitingForLoadExceptions.put(did, exception);
                                } else if (user.deleted) {
                                    continue;
                                }
                                if (currentType == TYPE_CATEGORY && isInCategory) {
                                    categoryDialogsResult.add(exception);
                                }
                                usersResult.add(exception);
                            } else {
                                TLRPC.Chat chat = getMessagesController().getChat(-lower_id);
                                if (chat == null) {
                                    chatsToLoad.add(-lower_id);
                                    waitingForLoadExceptions.put(did, exception);
                                    continue;
                                } else if (chat.left || chat.kicked || chat.migrated_to != null) {
                                    continue;
                                }
                                if (currentType == TYPE_CATEGORY && isInCategory) {
                                    categoryDialogsResult.add(exception);
                                }
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    channelsResult.add(exception);
                                } else {
                                    chatsResult.add(exception);
                                }
                            }
                        } else if (high_id != 0) {
                            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(high_id);
                            if (encryptedChat == null) {
                                encryptedChatsToLoad.add(high_id);
                                waitingForLoadExceptions.put(did, exception);
                            } else {
                                TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                                if (user == null) {
                                    usersToLoad.add(encryptedChat.user_id);
                                    waitingForLoadExceptions.put(encryptedChat.user_id, exception);
                                } else if (user.deleted) {
                                    continue;
                                }
                            }
                            if (currentType == TYPE_CATEGORY && isInCategory) {
                                categoryDialogsResult.add(exception);
                            }
                            usersResult.add(exception);
                        }
                    }
                }
            }
            if (waitingForLoadExceptions.size() != 0) {
                try {
                    if (!encryptedChatsToLoad.isEmpty()) {
                        getMessagesStorage().getEncryptedChatsInternal(TextUtils.join(",", encryptedChatsToLoad), encryptedChats, usersToLoad);
                    }
                    if (!usersToLoad.isEmpty()) {
                        getMessagesStorage().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        getMessagesStorage().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                for (int a = 0, size = chats.size(); a < size; a++) {
                    TLRPC.Chat chat = chats.get(a);
                    if (chat.left || chat.kicked || chat.migrated_to != null) {
                        continue;
                    }
                    NotificationsSettingsActivity.NotificationException exception = waitingForLoadExceptions.get(-chat.id);
                    waitingForLoadExceptions.remove(-chat.id);

                    if (exception != null) {
                        Long cid = (long) -chat.id;
                        if (currentType == TYPE_CATEGORY && categoryAllDialogs.contains(cid)) {
                            categoryDialogsResult.add(exception);
                        }
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            channelsResult.add(exception);
                        } else {
                            chatsResult.add(exception);
                        }
                    }
                }
                for (int a = 0, size = users.size(); a < size; a++) {
                    TLRPC.User user = users.get(a);
                    if (user.deleted) {
                        continue;
                    }
                    waitingForLoadExceptions.remove(user.id);
                }
                for (int a = 0, size = encryptedChats.size(); a < size; a++) {
                    TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                    waitingForLoadExceptions.remove(((long) encryptedChat.id) << 32);
                }
                for (int a = 0, size = waitingForLoadExceptions.size(); a < size; a++) {
                    long did = waitingForLoadExceptions.keyAt(a);
                    if ((int) did < 0) {
                        chatsResult.remove(waitingForLoadExceptions.valueAt(a));
                        channelsResult.remove(waitingForLoadExceptions.valueAt(a));
                        categoryDialogsResult.remove(waitingForLoadExceptions.valueAt(a));
                    } else {
                        usersResult.remove(waitingForLoadExceptions.valueAt(a));
                        categoryDialogsResult.remove(waitingForLoadExceptions.valueAt(a));
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putUsers(users, true);
                getMessagesController().putChats(chats, true);
                getMessagesController().putEncryptedChats(encryptedChats, true);
                if (currentType == NotificationsController.TYPE_PRIVATE) {
                    exceptions = usersResult;
                } else if (currentType == NotificationsController.TYPE_GROUP) {
                    exceptions = chatsResult;
                } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                    exceptions = channelsResult;
                } else {
                    exceptions = categoryDialogsResult;
                }
                updateRows();
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void updateRows() {
        rowCount = 0;
        if (currentType != -1) {
            alertRow = rowCount++;
            alertSection2Row = rowCount++;
            messageSectionRow = rowCount++;
            previewRow = rowCount++;
            messageLedRow = rowCount++;
            messageVibrateRow = rowCount++;
            if (currentType == NotificationsController.TYPE_CHANNEL) {
                messagePopupNotificationRow = -1;
            } else {
                messagePopupNotificationRow = rowCount++;
            }
            messageSoundRow = rowCount++;
            if (Build.VERSION.SDK_INT >= 21) {
                messagePriorityRow = rowCount++;
            } else {
                messagePriorityRow = -1;
            }
            groupSection2Row = rowCount++;
            if(currentType == TYPE_CATEGORY && !getMessagesController().categories_dict.get(categoryId).getDialogs().isEmpty()){
                exceptionsAddRow = rowCount++;
            }
        } else {
            alertRow = -1;
            alertSection2Row = -1;
            messageSectionRow = -1;
            previewRow = -1;
            messageLedRow = -1;
            messageVibrateRow = -1;
            messagePopupNotificationRow = -1;
            messageSoundRow = -1;
            messagePriorityRow = -1;
            groupSection2Row = -1;
            exceptionsAddRow = -1;
        }
        if (exceptions != null && !exceptions.isEmpty()) {
            exceptionsStartRow = rowCount;
            rowCount += exceptions.size();
            exceptionsEndRow = rowCount;
        } else {
            exceptionsStartRow = -1;
            exceptionsEndRow = -1;
        }
        if (currentType != -1 || exceptions != null && !exceptions.isEmpty()) {
            exceptionsSection2Row = rowCount++;
        } else {
            exceptionsSection2Row = -1;
        }
        if (exceptions != null && !exceptions.isEmpty()) {
            deleteAllRow = rowCount++;
            deleteAllSectionRow = rowCount++;
        } else {
            deleteAllRow = -1;
            deleteAllSectionRow = -1;
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null) {
                Ringtone rng = RingtoneManager.getRingtone(getParentActivity(), ringtone);
                if (rng != null) {
                    if (ringtone.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                        name = LocaleController.getString("SoundDefault", R.string.SoundDefault);
                    } else {
                        name = rng.getTitle(getParentActivity());
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();

            if (currentType == NotificationsController.TYPE_PRIVATE) {
                if (name != null && ringtone != null) {
                    editor.putString("GlobalSound", name);
                    editor.putString("GlobalSoundPath", ringtone.toString());
                } else {
                    editor.putString("GlobalSound", "NoSound");
                    editor.putString("GlobalSoundPath", "NoSound");
                }
            } else if (currentType == NotificationsController.TYPE_GROUP) {
                if (name != null && ringtone != null) {
                    editor.putString("GroupSound", name);
                    editor.putString("GroupSoundPath", ringtone.toString());
                } else {
                    editor.putString("GroupSound", "NoSound");
                    editor.putString("GroupSoundPath", "NoSound");
                }
            } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                if (name != null && ringtone != null) {
                    editor.putString("ChannelSound", name);
                    editor.putString("ChannelSoundPath", ringtone.toString());
                } else {
                    editor.putString("ChannelSound", "NoSound");
                    editor.putString("ChannelSoundPath", "NoSound");
                }
            } else if (currentType == TYPE_CATEGORY) {
                if (name != null && ringtone != null) {
                    editor.putString(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_SOUND, categoryId), name);
                    editor.putString(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_SOUND_PATH, categoryId), ringtone.toString());
                } else {
                    editor.putString(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_SOUND, categoryId), "NoSound");
                    editor.putString(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_SOUND_PATH, categoryId), "NoSound");
                }
            }
            editor.commit();
            getNotificationsController().updateServerNotificationsSettings(currentType);
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(requestCode);
            if (holder != null) {
                adapter.onBindViewHolder(holder, requestCode);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<NotificationsSettingsActivity.NotificationException> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private Runnable searchRunnable;
        private SearchAdapterHelper searchAdapterHelper;

        public SearchAdapter(Context context) {
            mContext = context;
            searchAdapterHelper = new SearchAdapterHelper(true);
            searchAdapterHelper.setDelegate((searchId) -> {
                if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress()) {
                    emptyView.showTextView();
                }
                notifyDataSetChanged();
            });
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (query == null) {
                searchResult.clear();
                searchResultNames.clear();
                searchAdapterHelper.mergeResults(null);
                searchAdapterHelper.queryServerSearch(null, true, currentType != NotificationsController.TYPE_PRIVATE, true, false, false, 0, false, 0, 0);
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> processSearch(query), 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                searchAdapterHelper.queryServerSearch(query, true, currentType != NotificationsController.TYPE_PRIVATE, true, false, false, 0, false, 0, 0);
                final ArrayList<NotificationsSettingsActivity.NotificationException> contactsCopy = new ArrayList<>(exceptions);
                Utilities.searchQueue.postRunnable(() -> {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<TLObject> resultArray = new ArrayList<>();
                    ArrayList<NotificationsSettingsActivity.NotificationException> exceptionsArray = new ArrayList<>();
                    ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                    String[] names = new String[2];
                    for (int a = 0; a < contactsCopy.size(); a++) {
                        NotificationsSettingsActivity.NotificationException exception = contactsCopy.get(a);

                        int lower_id = (int) exception.did;
                        int high_id = (int) (exception.did >> 32);
                        TLObject object = null;

                        if (lower_id != 0) {
                            if (lower_id > 0) {
                                TLRPC.User user = getMessagesController().getUser(lower_id);
                                if (user.deleted) {
                                    continue;
                                }
                                if (user != null) {
                                    names[0] = ContactsController.formatName(user.first_name, user.last_name);
                                    names[1] = user.username;
                                    object = user;
                                }
                            } else {
                                TLRPC.Chat chat = getMessagesController().getChat(-lower_id);
                                if (chat != null) {
                                    if (chat.left || chat.kicked || chat.migrated_to != null) {
                                        continue;
                                    }
                                    names[0] = chat.title;
                                    names[1] = chat.username;
                                    object = chat;
                                }
                            }
                        } else {
                            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(high_id);
                            if (encryptedChat != null) {
                                TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                                if (user != null) {
                                    names[0] = ContactsController.formatName(user.first_name, user.last_name);
                                    names[1] = user.username;
                                }
                            }
                        }

                        String originalName = names[0];
                        names[0] = names[0].toLowerCase();
                        String tName = LocaleController.getInstance().getTranslitString(names[0]);
                        if (names[0] != null && names[0].equals(tName)) {
                            tName = null;
                        }

                        int found = 0;
                        for (int b = 0; b < search.length; b++) {
                            String q = search[b];
                            if (names[0] != null && (names[0].startsWith(q) || names[0].contains(" " + q)) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                found = 1;
                            } else if (names[1] != null && names[1].startsWith(q)) {
                                found = 2;
                            }

                            if (found != 0) {
                                if (found == 1) {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName(originalName, null, q));
                                } else {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName("@" + names[1], null, "@" + q));
                                }
                                exceptionsArray.add(exception);
                                if (object != null) {
                                    resultArray.add(object);
                                }
                                break;
                            }
                        }
                    }
                    updateSearchResults(resultArray, exceptionsArray, resultArrayNames);
                });
            });
        }

        private void updateSearchResults(final ArrayList<TLObject> result, final ArrayList<NotificationsSettingsActivity.NotificationException> exceptions, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchRunnable = null;
                searchResult = exceptions;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(result);
                if (searching && !searchAdapterHelper.isSearchInProgress()) {
                    emptyView.showTextView();
                }
                notifyDataSetChanged();
            });
        }

        public Object getObject(int position) {
            if (position >= 0 && position < searchResult.size()) {
                return searchResult.get(position);
            } else {
                position -= searchResult.size() + 1;
                ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
                if (position >= 0 && position < globalSearch.size()) {
                    return searchAdapterHelper.getGlobalSearch().get(position);
                }
            }
            return null;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            int count = searchResult.size();
            ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
            if (!globalSearch.isEmpty()) {
                count += 1 + globalSearch.size();
            }
            return count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new UserCell(mContext, 4, 0, false, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 1:
                default: {
                    view = new GraySectionCell(mContext);
                    break;
                }
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    UserCell cell = (UserCell) holder.itemView;
                    if (position < searchResult.size()) {
                        cell.setException(searchResult.get(position), searchResultNames.get(position), position != searchResult.size() - 1);
                        cell.setAddButtonVisible(false);
                    } else {
                        position -= searchResult.size() + 1;
                        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
                        TLObject object = globalSearch.get(position);
                        cell.setData(object, null, LocaleController.getString("NotificationsOn", R.string.NotificationsOn), 0, position != globalSearch.size() - 1);
                        cell.setAddButtonVisible(true);
                    }
                    break;
                }
                case 1: {
                    GraySectionCell cell = (GraySectionCell) holder.itemView;
                    cell.setText(LocaleController.getString("AddToExceptions", R.string.AddToExceptions));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == searchResult.size()) {
                return 1;
            }
            return 0;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != 0 && type != 4;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new UserCell(mContext, 6, 0, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextColorCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                default:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == messageSectionRow) {
                        headerCell.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                    }
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    SharedPreferences preferences = getNotificationsSettings();
                    if (position == previewRow) {
                        boolean enabled;
                        if (currentType == NotificationsController.TYPE_PRIVATE) {
                            enabled = preferences.getBoolean("EnablePreviewAll", true);
                        } else if (currentType == NotificationsController.TYPE_GROUP) {
                            enabled = preferences.getBoolean("EnablePreviewGroup", true);
                        } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                            enabled = preferences.getBoolean("EnablePreviewChannel", true);
                        } else {
                            enabled = preferences.getBoolean(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_PREVIEW, categoryId), true);
                        }
                        checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), enabled, true);
                    }
                    break;
                }
                case 2: {
                    UserCell cell = (UserCell) holder.itemView;
                    NotificationsSettingsActivity.NotificationException exception = exceptions.get(position - exceptionsStartRow);
                    cell.setException(exception, null, position != exceptionsEndRow - 1);
                    break;
                }
                case 3: {
                    TextColorCell textColorCell = (TextColorCell) holder.itemView;
                    SharedPreferences preferences = getNotificationsSettings();
                    int color;
                    if (currentType == NotificationsController.TYPE_PRIVATE) {
                        color = preferences.getInt("MessagesLed", 0xff0000ff);
                    } else if (currentType == NotificationsController.TYPE_GROUP) {
                        color = preferences.getInt("GroupLed", 0xff0000ff);
                    } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                        color = preferences.getInt("ChannelLed", 0xff0000ff);
                    } else {
                        color = preferences.getInt(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_LED, categoryId), 0xff0000ff);
                    }
                    for (int a = 0; a < 9; a++) {
                        if (TextColorCell.colorsToSave[a] == color) {
                            color = TextColorCell.colors[a];
                            break;
                        }
                    }
                    textColorCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), color, true);
                    break;
                }
                case 4: {
                    if (position == deleteAllSectionRow || position == groupSection2Row && exceptionsSection2Row == -1 || position == exceptionsSection2Row && deleteAllRow == -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 5: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    SharedPreferences preferences = getNotificationsSettings();
                    if (position == messageSoundRow) {
                        String value;
                        if (currentType == NotificationsController.TYPE_PRIVATE) {
                            value = preferences.getString("GlobalSound", LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        } else if (currentType == NotificationsController.TYPE_GROUP) {
                            value = preferences.getString("GroupSound", LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                            value = preferences.getString("ChannelSound", LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        } else {
                            value = preferences.getString(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_SOUND, categoryId), LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        }
                        if (value.equals("NoSound")) {
                            value = LocaleController.getString("NoSound", R.string.NoSound);
                        }
                        textCell.setTextAndValue(LocaleController.getString("Sound", R.string.Sound), value, true);
                    } else if (position == messageVibrateRow) {
                        int value;
                        if (currentType == NotificationsController.TYPE_PRIVATE) {
                            value = preferences.getInt("vibrate_messages", 0);
                        } else if (currentType == NotificationsController.TYPE_GROUP) {
                            value = preferences.getInt("vibrate_group", 0);
                        } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                            value = preferences.getInt("vibrate_channel", 0);
                        } else {
                            value = preferences.getInt(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_VIBRATE, categoryId), 0);
                        }
                        if (value == 0) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDefault", R.string.VibrationDefault), true);
                        } else if (value == 1) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Short", R.string.Short), true);
                        } else if (value == 2) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled), true);
                        } else if (value == 3) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Long", R.string.Long), true);
                        } else if (value == 4) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("OnlyIfSilent", R.string.OnlyIfSilent), true);
                        }
                    } else if (position == messagePriorityRow) {
                        int value;
                        if (currentType == NotificationsController.TYPE_PRIVATE) {
                            value = preferences.getInt("priority_messages", 1);
                        } else if (currentType == NotificationsController.TYPE_GROUP) {
                            value = preferences.getInt("priority_group", 1);
                        } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                            value = preferences.getInt("priority_channel", 1);
                        } else {
                            value = preferences.getInt(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_PRIORITY, categoryId), 1);
                        }
                        if (value == 0) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh), false);
                        } else if (value == 1 || value == 2) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityUrgent", R.string.NotificationsPriorityUrgent), false);
                        } else if (value == 4) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityLow", R.string.NotificationsPriorityLow), false);
                        } else if (value == 5) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityMedium", R.string.NotificationsPriorityMedium), false);
                        }
                    } else if (position == messagePopupNotificationRow) {
                        int option;
                        if (currentType == NotificationsController.TYPE_PRIVATE) {
                            option = preferences.getInt("popupAll", 0);
                        } else if (currentType == NotificationsController.TYPE_GROUP) {
                            option = preferences.getInt("popupGroup", 0);
                        } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                            option = preferences.getInt("popupChannel", 0);
                        } else {
                            option = preferences.getInt(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_POPUP, categoryId), 0);
                        }
                        String value;
                        if (option == 0) {
                            value = LocaleController.getString("NoPopup", R.string.NoPopup);
                        } else if (option == 1) {
                            value = LocaleController.getString("OnlyWhenScreenOn", R.string.OnlyWhenScreenOn);
                        } else if (option == 2) {
                            value = LocaleController.getString("OnlyWhenScreenOff", R.string.OnlyWhenScreenOff);
                        } else {
                            value = LocaleController.getString("AlwaysShowPopup", R.string.AlwaysShowPopup);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PopupNotification", R.string.PopupNotification), value, true);
                    }
                    break;
                }
                case 6: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    checkCell.setDrawLine(false);
                    String text;
                    StringBuilder builder = new StringBuilder();
                    int offUntil;
                    SharedPreferences preferences = getNotificationsSettings();

                    if (currentType == NotificationsController.TYPE_PRIVATE) {
                        text = LocaleController.getString("NotificationsForPrivateChats", R.string.NotificationsForPrivateChats);
                        offUntil = preferences.getInt("EnableAll2", 0);
                    } else if (currentType == NotificationsController.TYPE_GROUP) {
                        text = LocaleController.getString("NotificationsForGroups", R.string.NotificationsForGroups);
                        offUntil = preferences.getInt("EnableGroup2", 0);
                    } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                        text = LocaleController.getString("NotificationsForChannels", R.string.NotificationsForChannels);
                        offUntil = preferences.getInt("EnableChannel2", 0);
                    } else {
                        text = LocaleController.getString("NotificationsForCategory", R.string.NotificationsForCategory);
                        offUntil = preferences.getInt(String.format(Locale.US, NotificationsController.SHARED_KEY_CATEGORY_ENABLED, categoryId), 0);
                    }
                    int currentTime = getConnectionsManager().getCurrentTime();
                    boolean enabled;
                    int iconType;
                    if (enabled = offUntil < currentTime) {
                        builder.append(LocaleController.getString("NotificationsOn", R.string.NotificationsOn));
                        iconType = 0;
                    } else if (offUntil - 60 * 60 * 24 * 365 >= currentTime) {
                        builder.append(LocaleController.getString("NotificationsOff", R.string.NotificationsOff));
                        iconType = 0;
                    } else {
                        builder.append(LocaleController.formatString("NotificationsOffUntil", R.string.NotificationsOffUntil, LocaleController.stringForMessageListDate(offUntil)));
                        iconType = 2;
                    }
                    checkCell.setTextAndValueAndCheck(text, builder, enabled, iconType, false);
                    break;
                }
                case 7: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == exceptionsAddRow) {
                        textCell.setTextAndIcon(LocaleController.getString("NotificationsAddAnException", R.string.NotificationsAddAnException), R.drawable.actions_addmember2, exceptionsStartRow != -1);
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    } else if (position == deleteAllRow) {
                        textCell.setText(LocaleController.getString("NotificationsDeleteAllException", R.string.NotificationsDeleteAllException), false);
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteRedText5);
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (exceptions == null || !exceptions.isEmpty()) {
                return;
            }
            boolean enabled = getNotificationsController().isGlobalNotificationsEnabled(currentType, categoryId);
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (holder.getAdapterPosition() == messageSectionRow) {
                        headerCell.setEnabled(enabled, null);
                    } else {
                        headerCell.setEnabled(true, null);
                    }
                    break;
                }
                case 1: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    textCell.setEnabled(enabled, null);
                    break;
                }
                case 3: {
                    TextColorCell textCell = (TextColorCell) holder.itemView;
                    textCell.setEnabled(enabled, null);
                    break;
                }
                case 5: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setEnabled(enabled, null);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == messageSectionRow) {
                return 0;
            } else if (position == previewRow) {
                return 1;
            } else if (position >= exceptionsStartRow && position < exceptionsEndRow) {
                return 2;
            } else if (position == messageLedRow) {
                return 3;
            } else if (position == groupSection2Row || position == alertSection2Row || position == exceptionsSection2Row || position == deleteAllSectionRow) {
                return 4;
            } else if (position == alertRow) {
                return 6;
            } else if (position == exceptionsAddRow || position == deleteAllRow) {
                return 7;
            } else {
                return 5;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, TextColorCell.class, TextSettingsCell.class, UserCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, 0, new Class[]{TextColorCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon),
        };
    }
}
