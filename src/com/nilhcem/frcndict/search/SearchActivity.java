package com.nilhcem.frcndict.search;

import java.util.Observable;
import java.util.Observer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.nilhcem.frcndict.AboutActivity;
import com.nilhcem.frcndict.ApplicationController;
import com.nilhcem.frcndict.R;
import com.nilhcem.frcndict.core.ClearableEditText;
import com.nilhcem.frcndict.core.ClearableEditText.ClearableTextObservable;
import com.nilhcem.frcndict.core.DictActivity;
import com.nilhcem.frcndict.meaning.WordMeaningActivity;

public final class SearchActivity extends DictActivity implements Observer {
	private SearchService mService;
	private TextView mInputText;
	private ListView mResultList;
	private Button mSearchButton;
	private SearchAdapter mSearchAdapter;
	private EndlessScrollListener mEndlessScrollListener;
	private Toast mPressBackTwiceToast = null;
	private Toast mSearchEmptyToast = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!isFinishing()) {
			setContentView(R.layout.search_dict);

			initResultList();
			initSearchButton();
			initService();
			initInputText();

			restore(savedInstanceState);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		changeSearchButtonBackground();
		mPressBackTwiceToast = null;
		mService.setLastBackPressTime(0l);
	}

	@Override
	protected void onPause() {
		cancelToastIfNotNull(mPressBackTwiceToast);
		cancelToastIfNotNull(mSearchEmptyToast);
		super.onPause();
	}

	@Override
	public void onBackPressed() {
		if (mService.isBackBtnPressedForTheFirstTime()) {
			mPressBackTwiceToast = Toast.makeText(this, R.string.search_press_back_twice_exit, SearchService.BACK_TO_EXIT_TIMER);
			mPressBackTwiceToast.show();
			mService.setLastBackPressTime(System.currentTimeMillis());
		} else {
			// It is a real exit, close DB
			mService.stopPreviousThread();
			db.close();
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.main_menu_about) {
			Intent intent = new Intent(this, AboutActivity.class);
			startActivity(intent);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putCharSequence("search", mInputText.getText());
		outState.putInt("cur-page", mEndlessScrollListener.getCurrentPage());
		outState.putBoolean("loading", mEndlessScrollListener.isLoading());
		outState.putInt("prev-total", mEndlessScrollListener.getPreviousTotal());
	}

	private void restore(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			mInputText.setText(savedInstanceState.getCharSequence("search"));
			mEndlessScrollListener.setCurrentPage(savedInstanceState.getInt("cur-page"));
			mEndlessScrollListener.setLoading(savedInstanceState.getBoolean("loading"));
			mEndlessScrollListener.setPreviousTotal(savedInstanceState.getInt("prev-total"));
		} else {
			mService.setSearchType(SearchService.SEARCH_UNDEFINED);
		}
	}

	// TODO: Deprecated
	// Saves the search adapter to keep results when application state change
	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mSearchAdapter != null) {
			return mSearchAdapter;
		}
		return super.onRetainNonConfigurationInstance();
	}

	@Override
	public void update(Observable observable, Object data) {
		if (observable instanceof EndlessScrollListener) {
			mService.runSearchThread((String) data, mInputText.getText().toString(), this);
		} else if (observable instanceof ClearableTextObservable) {
			clearResults(true);
			changeSearchButtonBackground();
		}
	}

	private void initResultList() {
		mEndlessScrollListener = new EndlessScrollListener();
		mEndlessScrollListener.addObserver(this);

		// TODO deprecated
		// Get the instance of the object that was stored if one exists
		if (getLastNonConfigurationInstance() != null) {
			mSearchAdapter = (SearchAdapter) getLastNonConfigurationInstance();
		} else {
			mSearchAdapter = new SearchAdapter(this, R.layout.search_dict_list_item, getLayoutInflater());
		}

		mResultList = (ListView) findViewById(R.id.searchList);
		mResultList.setAdapter(mSearchAdapter);
		mResultList.setOnScrollListener(mEndlessScrollListener);
		mResultList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view.getId() > 0) { // not loading
					Intent intent = new Intent(SearchActivity.this, WordMeaningActivity.class);
					intent.putExtra(WordMeaningActivity.ID_INTENT, view.getId());
					startActivity(intent);
				}
			}
		});
	}

	private void initSearchButton() {
		mSearchButton = (Button) findViewById(R.id.searchBtn);
		mSearchButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Switch search type
				if (mService.getSearchType() == SearchService.SEARCH_FRENCH) {
					mService.setSearchType(SearchService.SEARCH_PINYIN);
				} else if (mService.getSearchType() == SearchService.SEARCH_PINYIN) {
					mService.setSearchType(SearchService.SEARCH_FRENCH);
				} else {
					mService.setSearchType(SearchService.SEARCH_UNDEFINED);
				}
				runNewSearch(false);
			}
		});
	}
	private void initService() {
		mService = ((ApplicationController) getApplication()).getSearchDictService();
		mService.setAdapter(mSearchAdapter);
	}

	private void initInputText() {
		ClearableEditText clearableText = (ClearableEditText) findViewById(R.id.searchInput);
		clearableText.addObserver(this);

		mInputText = (TextView) clearableText.getEditText();
		mInputText.setHint(R.string.search_hint_text);
		mInputText.setNextFocusDownId(mInputText.getId());
		mInputText.setImeOptions(EditorInfo.IME_ACTION_SEARCH); // set search icon as the keyboard return key
		mInputText.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					if (keyCode == KeyEvent.KEYCODE_ENTER) {
						runNewSearch(true);
						// Hide keyboard
		                InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		                in.hideSoftInputFromWindow(mInputText.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
						return true;
					}
				}
				return false;
			}
		});
	}

	private void runNewSearch(boolean clearSearchType) {
		String text = mInputText.getText().toString();
		if (text.trim().length() == 0) {
			cancelToastIfNotNull(mSearchEmptyToast);
			mSearchEmptyToast = Toast.makeText(SearchActivity.this, R.string.search_empty_text, Toast.LENGTH_SHORT);
			mSearchEmptyToast.show();
		} else {
			clearResults(clearSearchType);
			mSearchAdapter.addLoading();
			mService.runSearchThread(null, mInputText.getText().toString(), this);
		}
	}

	private void clearResults(boolean clearSearchType) {
		if (clearSearchType) {
			mService.setSearchType(SearchService.SEARCH_UNDEFINED);
		}
		mSearchAdapter.clear();
		mEndlessScrollListener.reset();
		mService.stopPreviousThread();
	}

	public void changeSearchButtonBackground() {
		int res = 0;
		int searchType = mService.getSearchType();

		if (searchType == SearchService.SEARCH_UNDEFINED) {
			res = R.drawable.magnifier;
		} else if (searchType == SearchService.SEARCH_FRENCH) {
			res = R.drawable.magnifier_fr;
		} else { // hanzi - pinyin
			res = R.drawable.magnifier_cn;
		}
		mSearchButton.setBackgroundResource(res);
	}

	private void cancelToastIfNotNull(Toast toast) {
		if (toast != null) {
			toast.cancel();
		}
	}
}