/*
 * Copyright (C) 2021 Nain57
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.activity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.SmartAutoClickerService
import com.buzbuz.smartautoclicker.database.Repository
import com.buzbuz.smartautoclicker.database.domain.Scenario
import de.raphaelebner.roomdatabasebackup.core.RoomBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


/**
 * Entry point activity for the application.
 * Shown when the user clicks on the launcher icon for the application, this activity will displays the list of
 * available scenarios, if any. Upon selection of a scenario, if a permission is missing, the
 * [PermissionsDialogFragment] will be shown. Once all permissions are granted, the media projection start notification
 * is shown and if the user accept it, this activity is automatically closed, and the overlay menu will is shown.
 */
class ScenarioActivity : AppCompatActivity(), ScenarioListFragment.OnScenarioClickedListener,
    PermissionsDialogFragment.PermissionDialogListener {

    /** ViewModel providing the click scenarios data to the UI. */
    private val scenarioViewModel: ScenarioViewModel by viewModels()

    /** Starts the media projection permission dialog and handle the result. */
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    /** Scenario clicked by the user. */
    private var requestedScenario: Scenario? = null

    private var repository: Repository? = null;

    private var roomBackup: RoomBackup? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        this.repository = Repository.getRepository(this);
        this.roomBackup = RoomBackup(this);
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario)
        setSupportActionBar(findViewById(R.id.toolbar))

        supportActionBar?.title = resources.getString(R.string.activity_scenario_title)
        scenarioViewModel.stopScenario()

        screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != RESULT_OK) {
                Toast.makeText(this, "User denied screen sharing permission", Toast.LENGTH_SHORT).show()
            } else {
                scenarioViewModel.loadScenario(it.resultCode, it.data!!, requestedScenario!!)
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scenario_activity, menu)

        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                scenarioViewModel.updateSearchQuery(newText)
                return true
            }
        })
        val backupView = menu.findItem(R.id.action_backup)
        backupView.setOnMenuItemClickListener(object: MenuItem.OnMenuItemClickListener {
            override fun onMenuItemClick(menuItem: MenuItem?): Boolean {
//                lifecycleScope.launch {
//                    this@ScenarioActivity.repository!!.doUpgrade()
//                }
//                val dbfile: File = getDatabasePath(Repository.DB_NAME)

                this@ScenarioActivity.roomBackup!!
                .database( this@ScenarioActivity.repository!!.getDB())
                .enableLogDebug(true)
                .backupIsEncrypted(false)
                .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
                .apply {
                    onCompleteListener { success, message, exitCode ->
                        if (success){
                            restartApp(Intent(this@ScenarioActivity, ScenarioActivity::class.java))
                        }


                    }
                }
                .backup()

                return true
            }

        })

        var restoreView = menu.findItem(R.id.action_restore)
        restoreView.setOnMenuItemClickListener(object: MenuItem.OnMenuItemClickListener {
            override fun onMenuItemClick(menuItem: MenuItem?): Boolean {
                this@ScenarioActivity.roomBackup!!
                    .database( this@ScenarioActivity.repository!!.getDB())
                    .enableLogDebug(true)
                    .backupIsEncrypted(false)
                    .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)
                    .apply {
                        onCompleteListener { success, message, exitCode ->
                            restartApp(Intent(this@ScenarioActivity, ScenarioActivity::class.java))
                        }
                    }
                    .restore()

                return true
            }

        })
        val importView = menu.findItem(R.id.action_import)
        importView.setOnMenuItemClickListener(object: MenuItem.OnMenuItemClickListener {
            override fun onMenuItemClick(menuItem: MenuItem?): Boolean {
                lifecycleScope.launch {
                    this@ScenarioActivity.repository?.doUpgrade()
                }
                return true
            }
        })

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        SmartAutoClickerService.getLocalService(null)
    }

    override fun onClicked(scenario: Scenario) {
        requestedScenario = scenario

        if (!scenarioViewModel.arePermissionsGranted()) {
            PermissionsDialogFragment.newInstance().show(supportFragmentManager, "fragment_edit_name")
            return
        }

        onPermissionsGranted()
    }

    override fun onPermissionsGranted() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
