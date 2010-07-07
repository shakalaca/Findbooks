package com.corner23.android.findbooks;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
// import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class FindBooks extends Activity {
	private static final String TAG = "FindBooks";
	
	private EditText mEditText = null;
	// private Button mButtonISBNConvert = null;
	// private String isbn_origin = null;	

    /*
    private TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable s) {
			int length = s.length();
			if (length == 13) {
				mButtonISBNConvert.setEnabled(true);
		        mButtonISBNConvert.setClickable(true);
				mButtonISBNConvert.setText(R.string.btn_convert_isbn10_text);
			} else if ((length == 10) && isbn_origin != null) {
				mButtonISBNConvert.setEnabled(true);
		        mButtonISBNConvert.setClickable(true);
				mButtonISBNConvert.setText(R.string.btn_convert_restore_text);
			} else {
				mButtonISBNConvert.setEnabled(false);
		        mButtonISBNConvert.setClickable(false);
			}
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
	};
	*/

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
       
        if (mEditText == null) {
	        mEditText = (EditText) findViewById(R.id.edit_isbn);
	        // mEditText.addTextChangedListener(mTextWatcher);
        }

        /*
        if (mButtonISBNConvert == null) {
	        mButtonISBNConvert = (Button) findViewById(R.id.btn_isbn10);
	        mButtonISBNConvert.setClickable(false);
			mButtonISBNConvert.setEnabled(false);
	        mButtonISBNConvert.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mEditText == null) {
						return;
					}
					
					Editable text = mEditText.getText();
					if (text == null) {
						return;
					}
					
					// currently in ISBN-13 mode
					if (isbn_origin == null) {
						String input = text.toString();
						if (input.length() != 13) {
							return;
						}
						isbn_origin = input;
						String isbn10 = isbn_origin.substring(3, isbn_origin.length());
						mEditText.setText(isbn10);
						mEditText.setSelection(isbn10.length(), isbn10.length());
						mButtonISBNConvert.setText(R.string.btn_convert_restore_text);
					} else {
						mEditText.setText(isbn_origin);
						mEditText.setSelection(isbn_origin.length(), isbn_origin.length());
						mButtonISBNConvert.setText(R.string.btn_convert_isbn10_text);
						isbn_origin = null;
					}
				}}
	        );
        }
        */
        Button btn_scan_find = (Button) findViewById(R.id.btn_scan_find);
        btn_scan_find.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
		        BarCodeIntegrator.initiateScan(FindBooks.this);
			}}
        );

        Button btn_find = (Button) findViewById(R.id.btn_find);
        btn_find.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mEditText == null) {
					return;
				}
				
				Editable text = mEditText.getText();
				if (text == null) {
					return;
				}
				
				String isbn = text.toString();	
	    		FindbookByISBN_Advance(isbn);
			}}
        );
	}
    
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	if (resultCode == Activity.RESULT_CANCELED) {
    		Log.d(TAG, "Canceled by user");
			return;
		}
		
    	BarCodeIntentResult scanResult = BarCodeIntegrator.parseActivityResult(requestCode, resultCode, intent);
    	if (scanResult != null) {
    		String isbn = scanResult.getContents();
    		if (mEditText != null) {
    			mEditText.setText(isbn);
    			mEditText.setSelection(isbn.length(), isbn.length());
    		}
    		FindbookByISBN_Advance(isbn);
    	} else {
    		Log.d(TAG, "Nothing found..");
    	}
    }

    private void FindbookByISBN_Advance(String isbn) {
    	if (isbn == null) {
    		return;
    	}
    	
    	if (isbn.length() == 0) {
    		return;
    	}
    	Log.d(TAG, "isbn:" + isbn);
    	
    	Bundle bundle = new Bundle();
    	bundle.putString("ISBN", isbn);
    	Intent intent = new Intent(this, ShowBookInfo.class);
    	intent.putExtras(bundle);
    	startActivity(intent);
    }
}
