package it.liceoarzignano.bold.news;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;
import android.support.design.widget.BottomSheetDialog;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

import io.realm.Realm;
import it.liceoarzignano.bold.BoldApp;
import it.liceoarzignano.bold.ManagerActivity;
import it.liceoarzignano.bold.R;
import it.liceoarzignano.bold.firebase.BoldAnalytics;
import it.liceoarzignano.bold.ui.recyclerview.DividerDecoration;
import it.liceoarzignano.bold.ui.recyclerview.RecyclerViewExt;

public class NewsListActivity extends AppCompatActivity {

    private LinearLayout mEmptyLayout;
    private TextView mEmptyText;

    private NewsController mController;
    private NewsAdapter mAdapter;

    private CustomTabsClient mClient;
    private CustomTabsSession mCustomTabsSession;
    private CustomTabsServiceConnection mCustomTabsServiceConnection;
    private CustomTabsIntent mCustomTabIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerViewExt newsList = (RecyclerViewExt) findViewById(R.id.news_list);
        mEmptyLayout = (LinearLayout) findViewById(R.id.news_empty_layout);
        mEmptyText = (TextView) findViewById(R.id.news_empty_text);

        Intent callingIntent = getIntent();
        long id = callingIntent.getLongExtra("newsId", -1);

        if (id > 0) {
            News mCalledNews = Realm.getInstance(((BoldApp) getApplication()).getConfig())
                    .where(News.class).equalTo("id", id).findFirst();
            showUrl(mCalledNews.getUrl());
        }

        mController = new NewsController(((BoldApp) getApplication()).getConfig());

        newsList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        newsList.setItemAnimator(new DefaultItemAnimator());
        newsList.addItemDecoration(new DividerDecoration(getApplicationContext()));
        mAdapter = new NewsAdapter(mController.getAll(), this);
        newsList.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Chrome custom tabs
        setupCCustomTabs();

        String query = null;
        Intent callingIntent = getIntent();
        if (Intent.ACTION_SEARCH.equals(callingIntent.getAction())) {
            query = callingIntent.getStringExtra(SearchManager.QUERY);
        }

        refresh(query);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCustomTabsServiceConnection != null) {
            unbindService(mCustomTabsServiceConnection);
            mCustomTabsServiceConnection = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search, menu);
        setupSearchView(menu.findItem(R.id.menu_search));
        return true;
    }

    /**
     * Initialize search view
     *
     * @param item menu item
     */
    private void setupSearchView(MenuItem item) {
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);

        if (searchView == null) {
            return;
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String mQuery) {
                refresh(mQuery);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String mNewText) {
                refresh(mNewText);
                return true;
            }
        });
    }

    /**
     * Refresh list
     *
     * @param query search query
     */
    void refresh(String query) {
        final List<News> news = mController.getByQuery(query);
        mAdapter.updateList(news);
        mEmptyLayout.setVisibility(news.isEmpty() ? View.VISIBLE : View.GONE);
        mEmptyText.setText(getString(query != null && !query.isEmpty() ?
                R.string.search_no_result : R.string.news_empty));

    }

    /**
     * Initialize chrome custom tabs
     */
    private void setupCCustomTabs() {
        if (mCustomTabIntent != null) {
            return;
        }

        mCustomTabsServiceConnection = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName componentName,
                                                     CustomTabsClient customTabsClient) {
                mClient = customTabsClient;
                mClient.warmup(0L);
                mCustomTabsSession = mClient.newSession(null);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                        mClient = null;
                    }
        };

        CustomTabsClient.bindCustomTabsService(this, "com.android.chrome",
                mCustomTabsServiceConnection);

        mCustomTabIntent = new CustomTabsIntent.Builder(mCustomTabsSession)
                .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setShowTitle(true)
                .setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left)
                .setExitAnimations(this, android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right)
                .build();
    }

    /**
     * Open the url associated with a news
     *
     * @param url website url
     */
    void showUrl(String url) {
        new BoldAnalytics(this).log(FirebaseAnalytics.Event.VIEW_ITEM,
                FirebaseAnalytics.Param.ITEM_NAME, "News url");
        setupCCustomTabs();
        mCustomTabIntent.launchUrl(this, Uri.parse(url));
    }

    void viewNews(News news) {
        // Bottom sheet dialog
        final BottomSheetDialog sheet = new BottomSheetDialog(this);

        @SuppressLint("InflateParams")
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_sheet_news, null);
        LinearLayout shareLayout = (LinearLayout) sheetView.findViewById(R.id.news_sheet_share);
        LinearLayout eventLayout = (LinearLayout) sheetView.findViewById(R.id.news_sheet_to_event);
        LinearLayout deleteLayout = (LinearLayout) sheetView.findViewById(R.id.news_sheet_delete);

        shareLayout.setOnClickListener(view -> {
            new BoldAnalytics(this).log(FirebaseAnalytics.Event.SHARE,
                    FirebaseAnalytics.Param.ITEM_NAME, "Share news");
            sheet.hide();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, String.format("%1$s (%2$s)\n%3$s\n%4$s",
                            news.getTitle(), news.getDate(), news.getMessage(), news.getUrl()));
            startActivity(Intent.createChooser(shareIntent, getString(R.string.news_sheet_share)));

        });
        deleteLayout.setOnClickListener(view -> {
            new BoldAnalytics(this).log(FirebaseAnalytics.Event.SELECT_CONTENT,
                    FirebaseAnalytics.Param.ITEM_NAME, "Delete news");
            sheet.hide();
            NewsController controller = new NewsController(
                    ((BoldApp) getApplication()).getConfig());
            controller.delete(news.getId());
            refresh(null);
        });
        eventLayout.setOnClickListener(view -> {
            new BoldAnalytics(this).log(FirebaseAnalytics.Event.SELECT_CONTENT,
                    FirebaseAnalytics.Param.ITEM_NAME, "Convert news");
            sheet.hide();
            Intent toEventIntent = new Intent(this, ManagerActivity.class);
            toEventIntent.putExtra("newsToEvent", news.getId());
            toEventIntent.putExtra("isMark", false);
            startActivity(toEventIntent);
        });

        sheet.setContentView(sheetView);
        sheet.show();
    }
}
