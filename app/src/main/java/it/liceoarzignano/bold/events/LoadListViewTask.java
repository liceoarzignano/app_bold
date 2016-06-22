package it.liceoarzignano.bold.events;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.ListView;

import io.realm.Realm;
import io.realm.RealmResults;
import it.liceoarzignano.bold.BoldApp;
import it.liceoarzignano.bold.R;

class LoadListViewTask extends AsyncTask<Void, Void, Void> {

    private final Context context;
    private final ListView mEventsListView;

    LoadListViewTask(Context applicationContext, ListView mEventsListView) {
        this.context = applicationContext;
        this.mEventsListView = mEventsListView;
    }

    @Override
    protected Void doInBackground(Void... params) {
        return null;
    }

    @Override
    protected void onPostExecute(Void arg0) {
        Realm realm = Realm.getInstance(BoldApp.getAppRealmConfiguration());
        RealmResults<Event> events = realm.where(Event.class).findAllSorted("value");
        ListArrayAdapter listArrayAdapter = new ListArrayAdapter(context,
                R.layout.item_event, events);
        mEventsListView.setAdapter(listArrayAdapter);
    }
}
