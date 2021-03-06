package it.liceoarzignano.bold.events

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import it.liceoarzignano.bold.R
import it.liceoarzignano.bold.editor.EditorActivity
import it.liceoarzignano.bold.ui.ActionsDialog
import it.liceoarzignano.bold.ui.recyclerview.RecyclerViewExt
import it.liceoarzignano.bold.ui.recyclerview.ScrollListener
import it.liceoarzignano.bold.utils.SystemUtils
import it.liceoarzignano.bold.utils.Time


class EventListActivity : AppCompatActivity() {
    private lateinit var mCoordinator: androidx.coordinatorlayout.widget.CoordinatorLayout
    private lateinit var mEmptyLayout: LinearLayout
    private lateinit var mEmptyText: TextView

    private lateinit var mAdapter: EventsAdapter
    private lateinit var mEventsHandler: EventsHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
        toolbar.setNavigationOnClickListener { finish() }

        mCoordinator = findViewById(R.id.coordinator_layout)
        val eventList = findViewById<RecyclerViewExt>(R.id.event_list)
        mEmptyLayout = findViewById(R.id.event_empty_layout)
        mEmptyText = findViewById(R.id.events_empty_text)

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this, EditorActivity::class.java)
            intent.putExtra(EditorActivity.EXTRA_IS_MARK, false)
            startActivity(intent)
        }

        // Load by query for reverse order
        mEventsHandler = EventsHandler.getInstance(baseContext)
        mAdapter = EventsAdapter(mEventsHandler.getByQuery(null), this)
        eventList.adapter = mAdapter

        if (SystemUtils.isNotLegacy) {
            eventList.addOnScrollListener(ScrollListener(toolbar, resources, eventList.top))
        }
    }

    override fun onResume() {
        super.onResume()

        var query: String? = null
        val callingIntent = intent
        if (Intent.ACTION_SEARCH == callingIntent.action) {
            query = callingIntent.getStringExtra(SearchManager.QUERY)
        }

        refreshList(query)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)
        setupSearchView(menu.findItem(R.id.menu_search))
        return true
    }

    /**
     * Setup search menu item behaviour
     *
     * @param item search menu item
     */
    private fun setupSearchView(item: MenuItem) {
        val searchView = item.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                refreshList(query)
                return true
            }

            override fun onQueryTextChange(text: String): Boolean {
                refreshList(text)
                return true
            }
        })

        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                refreshList(null)
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                refreshList(null)
                return true
            }
        })
    }

    /**
     * Update the RecyclerView content
     *
     * @param query:   search query
     */
    private fun refreshList(query: String?) {
        val hasQuery = query != null && !query.isEmpty()

        val events = mEventsHandler.getByQuery(query)
        mAdapter.updateList(events)
        mEmptyLayout.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        mEmptyText.text = getString(if (hasQuery) R.string.search_no_result else R.string.events_empty)
    }

    internal fun eventActions(event: Event): Boolean {
        val dialog = ActionsDialog(this, true, false, event.id)
        dialog.setOnActionsListener(object : ActionsDialog.OnActionsDialogListener {
            override fun onShare() {
                val message = String.format("%1\$s (%2\$s)\n%3\$s", event.title,
                        Time(event.date).toString(), event.description)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, message),
                        getString(R.string.share_title)))
            }

            override fun onDelete() {
                mEventsHandler.delete(event.id)
                Snackbar.make(mCoordinator, getString(R.string.actions_removed), Snackbar.LENGTH_LONG)
                        .show()
                refreshList(null)
            }
        })
        dialog.show()

        return true
    }
}
