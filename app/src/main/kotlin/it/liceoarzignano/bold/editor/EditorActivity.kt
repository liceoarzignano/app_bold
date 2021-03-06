package it.liceoarzignano.bold.editor

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.text.SpannableStringBuilder
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import it.liceoarzignano.bold.R
import it.liceoarzignano.bold.events.Event
import it.liceoarzignano.bold.events.EventsHandler
import it.liceoarzignano.bold.marks.Mark
import it.liceoarzignano.bold.marks.MarksHandler
import it.liceoarzignano.bold.news.NewsHandler
import it.liceoarzignano.bold.settings.AppPrefs
import it.liceoarzignano.bold.utils.Time
import java.util.*

class EditorActivity : AppCompatActivity() {
    private lateinit var mCoordinator: androidx.coordinatorlayout.widget.CoordinatorLayout
    private lateinit var mTitleLayout: RelativeLayout
    private lateinit var mTitleText: EditText
    private lateinit var mInputTitle: TextInputLayout
    private lateinit var mSubjectLayout: RelativeLayout
    private lateinit var mSubjectView: EditText
    private lateinit var mNotesText: EditText
    private lateinit var mCategoryLayout: RelativeLayout
    private lateinit var mCategorySpinner: Spinner
    private lateinit var mValueLayout: RelativeLayout
    private lateinit var mValueView: EditText
    private lateinit var mDateView: EditText

    private lateinit var mTime: Time
    private lateinit var mDateSetListener: DatePickerDialog.OnDateSetListener
    private lateinit var mMarksHandler: MarksHandler
    private lateinit var mEventsHandler: EventsHandler
    private lateinit var mPrefs: AppPrefs

    private var mId = 0L
    private var mIsEdit = false
    private var mIsMark = false
    private var mValue = 0
    private var mDialogVal = 0.toDouble()

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        mId = intent.getLongExtra(EXTRA_ID, -1)
        mIsEdit = mId != -1L
        mIsMark = intent.getBooleanExtra(EXTRA_IS_MARK, true)

        mMarksHandler = MarksHandler.getInstance(baseContext)
        mEventsHandler = EventsHandler.getInstance(baseContext)
        mPrefs = AppPrefs(baseContext)

        setContentView(R.layout.activity_editor)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = getString(
                if (mIsMark)
                    if (mIsEdit) R.string.editor_update_mark else R.string.editor_new_mark
                else
                    if (mIsEdit) R.string.editor_update_event else R.string.editor_new_event)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_remove)
        toolbar.setNavigationOnClickListener { askQuit() }

        mCoordinator = findViewById(R.id.coordinator_layout)
        mTitleLayout = findViewById(R.id.editor_title_layout)
        mTitleText = findViewById(R.id.editor_title_text)
        mInputTitle = findViewById(R.id.editor_input_title)
        mSubjectView = findViewById(R.id.editor_subject_selector)
        mSubjectLayout = findViewById(R.id.editor_subject_layout)
        mNotesText = findViewById(R.id.editor_notes_text)
        mCategoryLayout = findViewById(R.id.editor_category_layout)
        mCategorySpinner = findViewById(R.id.editor_category_spinner)
        mValueLayout = findViewById(R.id.editor_value_layout)
        mValueView = findViewById(R.id.editor_value_view)
        mDateView = findViewById(R.id.editor_date_view)

        if (mIsEdit) {
            if (intent.getBooleanExtra(EXTRA_IS_NEWS, false)) {
                loadNews()
            } else {
                loadUi()
            }
        } else {
            mTime = Time()
        }

        if (mIsMark) {
            initMarkUi()
        } else {
            initEventUi()
        }

        mDateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            mTime = Time(year, month, dayOfMonth)
            mDateView.text = SpannableStringBuilder(mTime.toString())
        }
        mDateView.setOnClickListener { showDatePicker() }
        mDateView.text = SpannableStringBuilder(mTime.toString())

        mSubjectView.keyListener = null
        mValueView.keyListener = null
        mDateView.keyListener = null
    }

    override fun onBackPressed() = askQuit()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save) {
            save()
        }

        return super.onOptionsItemSelected(item)
    }


    private fun initMarkUi() {
        if (mPrefs.get(AppPrefs.KEY_IS_TEACHER)) {
            mTitleLayout.visibility = View.VISIBLE
            mInputTitle.hint = getString(R.string.editor_hint_student)
        } else {
            mSubjectLayout.visibility = View.VISIBLE
        }
        mValueLayout.visibility = View.VISIBLE
        mValueView.text = SpannableStringBuilder(String.format(Locale.ENGLISH,
                "%.2f", mValue.toDouble() / 100))

        val items: Array<String> = when (mPrefs.get(AppPrefs.KEY_ADDRESS, "0")) {
            "1" -> resources.getStringArray(R.array.subjects_lists_1)
            "2" -> resources.getStringArray(R.array.subjects_lists_2)
            "3" -> resources.getStringArray(R.array.subjects_lists_3)
            "4" -> resources.getStringArray(R.array.subjects_lists_4)
            "5" -> resources.getStringArray(R.array.subjects_lists_5)
            else -> resources.getStringArray(R.array.subjects_lists_0)
        }

        val list: MutableList<CharSequence> = arrayListOf()
        items.forEach { list.add(it) }
        mSubjectView.setOnClickListener {
            MaterialDialog.Builder(this)
                    .title(R.string.editor_hint_subject)
                    .items(list)
                    .itemsCallback { _, _, _, text ->
                        mSubjectView.text = SpannableStringBuilder(text)
                    }
                    .show()
        }


        mValueView.setOnClickListener {
            MaterialDialog.Builder(this)
                    .title(R.string.editor_dialog_mark_title)
                    .customView(valuePickerView, false)
                    .positiveText(android.R.string.ok)
                    .neutralText(android.R.string.cancel)
                    .onPositive { _, _ ->
                        mValue = (mDialogVal * 25).toInt()
                        mValueView.text = SpannableStringBuilder(String.format(Locale.ENGLISH,
                                "%.2f", mDialogVal / 4))
                    }
                    .show()
        }
    }

    private fun initEventUi() {
        mTitleLayout.visibility = View.VISIBLE
        mInputTitle.hint = getString(R.string.editor_hint_event)
        mCategoryLayout.visibility = View.VISIBLE

        val items = resources.getStringArray(R.array.event_categories)
        mCategorySpinner.adapter = ArrayAdapter(this,
                android.R.layout.simple_dropdown_item_1line, items)
        mCategorySpinner.setSelection(mValue)
    }

    private fun loadUi() {
        if (mIsMark) {
            val mark = mMarksHandler[mId]
            if (mark == null) {
                mIsEdit = false
                return
            }
            if (mPrefs.get(AppPrefs.KEY_IS_TEACHER)) {
                mTitleText.setText(mark.subject)
            } else {
                mSubjectView.text = SpannableStringBuilder(mark.subject)
            }
            mNotesText.setText(mark.description)
            mTime = Time(mark.date)
            mValue = mark.value
        } else {
            val event = mEventsHandler[mId]
            if (event == null) {
                mIsEdit = false
                return
            }
            mTitleText.setText(event.title)
            mNotesText.setText(event.description)
            mValue = event.category
            mTime = Time(event.date)
        }

        mDateView.text = SpannableStringBuilder(mTime.toString())
    }

    private fun loadNews() {
        val handler = NewsHandler.getInstance(this)
        val news = handler[mId] ?: return

        mIsMark = false
        mIsEdit = false
        mTime = Time(news.date)
        mTitleText.setText(news.title)
        mDateView.text = SpannableStringBuilder(mTime.toString())
        mNotesText.setText(String.format("%1\$s\n%2\$s", news.description, news.url))
        mValue = 6
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = mTime
        DatePickerDialog(this, mDateSetListener, calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                .show()
    }

    private fun askQuit() {
        MaterialDialog.Builder(this)
                .content(R.string.editor_cancel_message)
                .positiveText(android.R.string.yes)
                .negativeText(android.R.string.no)
                .onPositive { _, _ -> finish() }
                .show()
    }

    private val valuePickerView: View
        get() {
            val viewGroup = findViewById<ViewGroup>(R.id.dialog_root)
            val dialog = LayoutInflater.from(this).inflate(R.layout.dialog_seekbar, viewGroup)
            val preview = dialog.findViewById<TextView>(R.id.dialog_value)
            preview.text = String.format(Locale.ENGLISH, "%.2f", mValue.toDouble() / 100)

            mDialogVal = (mValue / 25).toDouble()

            val seekBar = dialog.findViewById<SeekBar>(R.id.dialog_seek_bar)
            seekBar.progress = mValue / 40
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    preview.text = String.format(Locale.ENGLISH, "%.2f", progress.toDouble() / 4)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    mDialogVal = seekBar.progress.toDouble()
                    preview.text = String.format(Locale.ENGLISH, "%.2f", mDialogVal / 4)
                }
            })

            return dialog
        }

    private fun save() = if (mIsMark) {
        if (mValue == 0 || mTitleText.text.toString().isEmpty() && mSubjectView.text.toString().isEmpty()) {
            Snackbar.make(mCoordinator, getString(R.string.editor_error),
                    Snackbar.LENGTH_SHORT).show()
        } else {
            saveMark()
        }
    } else {
        if (mTitleText.text.toString().isEmpty()) {
            Snackbar.make(mCoordinator, getString(R.string.editor_error),
                    Snackbar.LENGTH_SHORT).show()
        } else {
            saveEvent()
        }
    }

    private fun saveMark() {
        val mark = Mark()
        mark.subject = (if (mPrefs.get(AppPrefs.KEY_IS_TEACHER))
            mTitleText.text
        else
            mSubjectView.text).toString()
        mark.value = mValue
        mark.date = mTime.time
        mark.isFirstQuarter = mTime.isFirstQuarter(baseContext)
        mark.description = mNotesText.text.toString()

        if (mIsEdit) {
            mark.id = mId
            mMarksHandler.update(mark)
        } else {
            mMarksHandler.add(mark)
        }

        Snackbar.make(mCoordinator, getString(R.string.editor_saved), Snackbar.LENGTH_LONG).show()
        Handler().postDelayed({ this.finish() }, 800)
    }

    private fun saveEvent() {
        val event = Event()
        event.title = mTitleText.text.toString()
        event.category = mCategorySpinner.selectedItemPosition
        event.date = mTime.time
        event.description = mNotesText.text.toString()

        if (mIsEdit) {
            event.id = mId
            mEventsHandler.update(event)
        } else {
            mEventsHandler.add(event)
        }

        Snackbar.make(mCoordinator, getString(R.string.editor_saved), Snackbar.LENGTH_LONG).show()
        Handler().postDelayed({ this.finish() }, 800)
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_IS_MARK = "extra_is_mark"
        const val EXTRA_IS_NEWS = "extra_is_news"
    }
}
