/*******************************************************************************
 * Mirakel is an Android App for managing your ToDo-Lists
 *
 *   Copyright (c) 2013-2015 Anatolij Zelenin, Georg Semmler.
 *
 *       This program is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU General Public License as published by
 *       the Free Software Foundation, either version 3 of the License, or
 *       any later version.
 *
 *       This program is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU General Public License for more details.
 *
 *       You should have received a copy of the GNU General Public License
 *       along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package de.azapps.mirakel.new_ui.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.gc.materialdesign.views.ButtonFloat;
import com.google.common.base.Optional;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.listeners.EventListener;
import com.shamanland.fab.FloatingActionButton;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.azapps.material_elements.utils.ThemeManager;
import de.azapps.mirakel.DefinitionsHelper;
import de.azapps.mirakel.adapter.OnItemClickedListener;
import de.azapps.mirakel.adapter.SimpleModelListAdapter;
import de.azapps.mirakel.helper.Helpers;
import de.azapps.mirakel.helper.MirakelCommonPreferences;
import de.azapps.mirakel.helper.TaskHelper;
import de.azapps.mirakel.model.ModelBase;
import de.azapps.mirakel.model.account.AccountMirakel;
import de.azapps.mirakel.model.list.ListMirakel;
import de.azapps.mirakel.model.task.Task;
import de.azapps.mirakel.new_ui.fragments.ListEditFragment;
import de.azapps.mirakel.new_ui.fragments.ListsFragment;
import de.azapps.mirakel.new_ui.fragments.TaskFragment;
import de.azapps.mirakel.new_ui.fragments.TasksFragment;
import de.azapps.mirakel.settings.SettingsActivity;
import de.azapps.mirakelandroid.R;
import de.azapps.tools.Log;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Optional.of;
import static de.azapps.tools.OptionalUtils.Procedure;
import static de.azapps.tools.OptionalUtils.withOptional;

public class MirakelActivity extends ActionBarActivity implements OnItemClickedListener<ModelBase>,
    EventListener, LockableDrawer {

    private static final String TAG = "MirakelActivity";
    private Optional<DrawerLayout> mDrawerLayout = absent();
    private Optional<ActionBarDrawerToggle> mDrawerToggle = absent();


    class ActionBarViewHolder {
        @butterknife.Optional
        @InjectView(R.id.actionbar_switcher)
        @Nullable
        ViewSwitcher actionbarSwitcher;
        @InjectView(R.id.actionbar_spinner)
        @NonNull
        Spinner actionbarSpinner;
        @butterknife.Optional
        @InjectView(R.id.actionbar_title)
        @Nullable
        TextView actionbarTitle;

        private ActionBarViewHolder(final View v) {
            ButterKnife.inject(this, v);
        }
    }

    private ActionBarViewHolder actionBarViewHolder;

    @NonNull
    @InjectView(R.id.actionbar)
    Toolbar actionbar;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Getter / Setter
    private TasksFragment getTasksFragment() {
        return (TasksFragment) getSupportFragmentManager().findFragmentById(R.id.tasks_fragment);
    }

    private ListsFragment getListsFragment() {
        return (ListsFragment) getSupportFragmentManager().findFragmentById(R.id.lists_fragment);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Override functions

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirakel);
        ButterKnife.inject(this);
        initDrawer();
        handleIntent(getIntent());
        if ((getTasksFragment() != null) && (getTasksFragment().getList() != null)) {
            setSupportActionBar(actionbar);
            setupActionbar();
        }
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        withOptional(mDrawerToggle, new Procedure<ActionBarDrawerToggle>() {
            @Override
            public void apply(final ActionBarDrawerToggle input) {
                input.syncState();
            }
        });
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        withOptional(mDrawerToggle, new Procedure<ActionBarDrawerToggle>() {
            @Override
            public void apply(final ActionBarDrawerToggle input) {
                input.onConfigurationChanged(newConfig);
            }
        });
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        if (mDrawerLayout.isPresent()) {
            // For phones
            final boolean drawerOpen = mDrawerLayout.get().isDrawerOpen(Gravity.START);
            if (drawerOpen) {
                getMenuInflater().inflate(R.menu.lists_menu, menu);
            } else {
                getMenuInflater().inflate(R.menu.tasks_menu, menu);
            }
        } else {
            getMenuInflater().inflate(R.menu.tablet_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        if (mDrawerToggle.isPresent()) {
            // Phone
            if (mDrawerToggle.get().onOptionsItemSelected(item)) {
                return true;
            }
        }
        final int id = item.getItemId();
        if (id == R.id.action_settings) {
            final Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_toggle_ui) {
            MirakelCommonPreferences.setUseNewUI(false);
            Helpers.restartApp(this);
            return true;
        } else if (id == R.id.action_create_list) {
            final DialogFragment newFragment = ListEditFragment.newInstance(ListMirakel.getStub());
            newFragment.show(getSupportFragmentManager(), "dialog");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Other functions

    private void setList(final ListMirakel listMirakel) {
        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        final TasksFragment tasksFragment = getTasksFragment();
        tasksFragment.setList(listMirakel);
        fragmentTransaction.commit();
    }

    private void handleIntent(final Intent intent) {
        if (intent == null) {
            return;
        }
        switch (intent.getAction()) {
        case DefinitionsHelper.SHOW_TASK:
        case DefinitionsHelper.SHOW_TASK_FROM_WIDGET:
        case DefinitionsHelper.SHOW_TASK_REMINDER:
            final Optional<Task> task = TaskHelper.getTaskFromIntent(intent);
            if (task.isPresent()) {
                setList(task.get().getList());
                selectTask(task.get());
            }
            break;
        case Intent.ACTION_SEND:
        case Intent.ACTION_SEND_MULTIPLE:
            // TODO
            break;
        case DefinitionsHelper.SHOW_LIST:
        case DefinitionsHelper.SHOW_LIST_FROM_WIDGET:
            if (intent.hasExtra(DefinitionsHelper.EXTRA_LIST)) {
                setList((ListMirakel) intent.getParcelableExtra(DefinitionsHelper.EXTRA_LIST));
            } else {
                Log.d(TAG, "show_list does not pass list, so ignore this");
            }
            break;
        case Intent.ACTION_SEARCH:
            // TODO
            break;
        case DefinitionsHelper.ADD_TASK_FROM_WIDGET:
            // TODO
            break;
        case DefinitionsHelper.SHOW_MESSAGE:
            // TODO
            break;
        }
    }

    private void initDrawer() {
        // Nav drawer
        mDrawerLayout = fromNullable((DrawerLayout) findViewById(R.id.drawer_layout));
        withOptional(mDrawerLayout, new Procedure<DrawerLayout>() {
            @Override
            public void apply(final DrawerLayout mDrawerLayout) {
                mDrawerLayout.setScrimColor(ThemeManager.getColor(R.attr.colorNavDrawerScrim));
                mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START);
                final ActionBarDrawerToggle mDrawerToggle = new DrawerToggle(mDrawerLayout);
                mDrawerLayout.setDrawerListener(mDrawerToggle);
                MirakelActivity.this.mDrawerToggle = of(mDrawerToggle);
            }
        });
    }

    @Override
    public void lockDrawer() {
        withOptional(mDrawerLayout, new Procedure<DrawerLayout>() {
            @Override
            public void apply(final DrawerLayout drawerLayout) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
            }
        });
    }

    @Override
    public void unlockDrawer() {
        withOptional(mDrawerLayout, new Procedure<DrawerLayout>() {
            @Override
            public void apply(final DrawerLayout drawerLayout) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }
        });
    }


    private void updateToolbar(final boolean showSwitcher) {
        if (actionBarViewHolder.actionbarSwitcher != null) {
            // This is for cases when the app wake up in a strange state
            if (showSwitcher && actionBarViewHolder.actionbarSwitcher.getDisplayedChild() == 0) {
                actionBarViewHolder.actionbarSwitcher.showNext();
            } else if (!showSwitcher && actionBarViewHolder.actionbarSwitcher.getDisplayedChild() == 1) {
                actionBarViewHolder.actionbarSwitcher.showPrevious();
            }
        }
        if (!showSwitcher) {
            if (actionBarViewHolder.actionbarTitle != null) {
                actionBarViewHolder.actionbarTitle.setText(getTasksFragment().getList().getName());
            }
        }
    }

    private void setupActionbar() {
        setTitle(null);
        final View actionbarLayout = LayoutInflater.from(this).inflate(R.layout.actionbar_layout,
                                     actionbar, false);
        actionBarViewHolder = new ActionBarViewHolder(actionbarLayout);
        actionbar.addView(actionbarLayout);


        final Cursor cursor = AccountMirakel.allCursorWithAllAccounts();
        final SimpleModelListAdapter<AccountMirakel> adapter = new SimpleModelListAdapter<>(this, cursor, 0,
                AccountMirakel.class, R.layout.simple_list_row_with_bold_header);
        actionBarViewHolder.actionbarSpinner.setAdapter(adapter);
        actionBarViewHolder.actionbarSpinner.setOnItemSelectedListener(new
        AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view, final int position,
                                       final long id) {

                final AccountMirakel accountMirakel = adapter.getItem(position);
                getListsFragment().setAccount(of(accountMirakel));
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                // do nothing
            }
        });
        if (actionBarViewHolder.actionbarTitle != null) {
            actionBarViewHolder.actionbarTitle.setText(getTasksFragment().getList().getName());
        }
    }

    @Override
    public void onItemSelected(final @NonNull ModelBase item) {
        if (item instanceof Task) {
            selectTask((Task) item);
        } else if (item instanceof ListMirakel) {
            selectList((ListMirakel) item);
        }
    }

    private void selectList(ListMirakel item) {
        setList(item);
        withOptional(mDrawerLayout, new Procedure<DrawerLayout>() {
            @Override
            public void apply(final DrawerLayout input) {
                input.closeDrawer(Gravity.START);
            }
        });
    }

    private void selectTask(final Task item) {
        final DialogFragment newFragment = TaskFragment.newInstance((Task) item);
        newFragment.show(getSupportFragmentManager(), "dialog");
    }

    private boolean shouldShowSpinner() {
        return !mDrawerLayout.isPresent() || mDrawerLayout.get().isDrawerOpen(Gravity.START);
    }

    // Snackbar stuff
    @Override
    public void onShow(final Snackbar snackbar) {
        final ButtonFloat fab = getTasksFragment().floatingActionButton;
        // Have to set the animation in code because I can not change the toDeltaY at runtime :(
        // And I do not know it before
        final TranslateAnimation anim = new TranslateAnimation(0, 0, 0, -snackbar.getHeight());
        anim.setDuration(getResources().getInteger(R.integer.anim_snackbar_duration));
        anim.setFillEnabled(true);
        anim.setFillAfter(true);
        anim.setInterpolator(this, R.interpolator.decelerate_cubic);
        fab.startAnimation(anim);
    }

    @Override
    public void onShown(final Snackbar snackbar) {

    }

    @Override
    public void onDismiss(final Snackbar snackbar) {
        final ButtonFloat fab = getTasksFragment().floatingActionButton;
        final TranslateAnimation anim = new TranslateAnimation(0, 0, -snackbar.getHeight(), 0);
        anim.setDuration(getResources().getInteger(R.integer.anim_snackbar_duration));
        anim.setFillEnabled(true);
        anim.setFillAfter(true);
        anim.setInterpolator(this, R.interpolator.accelerate_cubic);
        fab.startAnimation(anim);
    }

    @Override
    public void onDismissed(final Snackbar snackbar) {

    }

    private class DrawerToggle extends ActionBarDrawerToggle {
        public DrawerToggle(DrawerLayout mDrawerLayout) {
            super(MirakelActivity.this, mDrawerLayout, MirakelActivity.this.actionbar, R.string.list_title,
                  R.string.list_title);
        }

        /**
         * Called when a drawer has settled in a completely closed state.
         */
        @Override
        public void onDrawerClosed(final View drawerView) {
            super.onDrawerClosed(drawerView);
            invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            getListsFragment().onCloseNavDrawer();
            updateToolbar(false);
            invalidateOptionsMenu();
        }

        /**
         * Called when a drawer has settled in a completely open state.
         */
        @Override
        public void onDrawerOpened(final View drawerView) {
            super.onDrawerOpened(drawerView);
            invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            updateToolbar(true);
            invalidateOptionsMenu();
        }
    }

}
