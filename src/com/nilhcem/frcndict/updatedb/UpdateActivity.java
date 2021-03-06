package com.nilhcem.frcndict.updatedb;

import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;

import com.nilhcem.frcndict.CheckDataActivity;
import com.nilhcem.frcndict.R;
import com.nilhcem.frcndict.core.layout.ProgressBar;
import com.nilhcem.frcndict.search.SearchActivity;
import com.nilhcem.frcndict.utils.Compatibility;

public final class UpdateActivity extends AbstractImportUpdateActivity {
	private ProgressBar mBackupProgress;
	private ProgressBar mRestoreProgress;

	public UpdateActivity() {
		super();
		mImport = false;

		mStartServiceListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startProcess(null);
			}
		};

		mCompletedListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
				Intent intent = new Intent(UpdateActivity.this, SearchActivity.class);
				if (Compatibility.isCompatible(11)) {
					flags |= Intent.FLAG_ACTIVITY_CLEAR_TASK;
				}
				intent.addFlags(flags);
				stopActivityAndStartIntent(intent);
			}
		};
	}

	@Override
	public void onBackPressed() {
		if (ImportUpdateService.getInstance() == null
				|| ImportUpdateService.getInstance().getStatus() == ImportUpdateService.STATUS_UNSTARTED) {
			super.onBackPressed();
		} else {
			moveTaskToBack(true); // act like home button (because we can't use the application when it is updating dictionary)
		}
	}

	@Override
	public void updateProgressData(int progressId, Integer progress) {
		if (progressId == ImportUpdateService.PROGRESS_BAR_BACKUP) {
			mBackupProgress.setProgress(progress);
		} else if (progressId == ImportUpdateService.PROGRESS_BAR_RESTORE) {
			mRestoreProgress.setProgress(progress);
		} else {
			super.updateProgressData(progressId, progress);
		}
	}

	@Override
	protected void initProgressData() {
		super.initProgressData();

		mBackupProgress = (ProgressBar) findViewById(R.id.importBackupProgress);
		mBackupProgress.setTitle(R.string.update_backup_text);
		mBackupProgress.setVisibility(View.VISIBLE);

		mRestoreProgress = (ProgressBar) findViewById(R.id.importRestoreProgress);
		mRestoreProgress.setTitle(R.string.update_restore_text);
		mRestoreProgress.setVisibility(View.VISIBLE);
	}

	@Override
	protected void refreshProgresses() {
		ImportUpdateService service = ImportUpdateService.getInstance();

		if (service != null) {
			mBackupProgress.setProgress(service.getPercent(ImportUpdateService.PROGRESS_BAR_BACKUP));
			mRestoreProgress.setProgress(service.getPercent(ImportUpdateService.PROGRESS_BAR_RESTORE));
		}
		super.refreshProgresses();
	}

	@Override
	protected void onCancelProgressButtonClicked() {
		super.onCancelProgressButtonClicked();
		if (!mLocalInstall) {
			CheckDataActivity.displayUpdateNotification(this);
		}
	}
}
