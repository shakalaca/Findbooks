package com.corner23.android.findbooks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class FindBooks extends Activity {
	private static final String TAG = "FindBooks";
	
	private static final int BOOKSTORE_FINDBOOK = 1;
	private static final int BOOKSTORE_ESLITE = 2;
	private static final int BOOKSTORE_BOOKS = 3;
	private static final int BOOKSTORE_SANMIN = 4;
	private static final int BOOKSTORE_KINGSTONE = 5;
	private static final int BOOKSTORE_TENLONG = 6;
	
	private static final String URL_FINDBOOK = "http://findbook.tw/m/book/%s/price";
	private static final String URL_ESLITE = "http://www.eslite.com/search_pro.aspx?query=%s";
	private static final String URL_BOOKS = "http://search.books.com.tw/exep/prod_search.php?key=%s";
	private static final String URL_SANMIN = "http://www.sanmin.com.tw/page-qsearch.asp?qu=%s";
	private static final String URL_KINGSTONE = "http://search.kingstone.com.tw/SearchResult.asp?c_name=%s";
	private static final String URL_TENLONG = "http://tlsj.tenlong.com.tw/WebModule/BookSearch/bookSearchAction.do?fkeyword=%s";
		// "http://tlsj.tenlong.com.tw/WebModule/BookSearch/bookSearchAdvancedAction.do?bookname=&submit=Submit&author=&isbn=%s&publisher_id=&chinese=&pub_date=&fpub_date_year=&book_order=";
	
	private static final String URL_BOOKS_BOOK = "http://www.books.com.tw/exep/prod/booksfile.php?item=%s";
	private static final String URL_SANMIN_BOOK = "http://www.sanmin.com.tw/QueryBookNmSort.asp?%s";
	
	private EditText mEditText = null;
	private Spinner mSpinner = null;
	private BooksAdapter mAdapter = null;
	private Button mButtonISBNConvert = null;
	private ProgressDialog mProgressDialog = null;

	private boolean mResumeTask = false;
	private String mISBN = null;
	private int mStore;
	
	private String isbn_origin = null;
	
	private FetchWebPageTask mFetchWebPageTask = null;
	
	static class ViewHolder {
		TextView isbn;
		TextView store;
		TextView price;
	}
	
	class SearchItems {
		String isbn;
		int bookstore;
		String name;
		String url;
		String price;
	}    	

	/*
	 * Utility function
	 */
	private void displayProgressBar(int resid) {
		if (null == mProgressDialog) {
			mProgressDialog = ProgressDialog.show(this, null, getResources().getString(resid), false, true);
			mProgressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface arg0) {
					if (mFetchWebPageTask != null) {
						mFetchWebPageTask.cancel(true);
						mFetchWebPageTask = null;
					}
				}
			});
		} else {
			mProgressDialog.setMessage(getResources().getString(resid));
		}		
	}
	
	private void dismissProgressBar() {
		if (null != mProgressDialog) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
	}
    
     /*
      * Async task for retrieving web pages by store
      */
    private class FetchWebPageTask extends AsyncTask<String, Void, Boolean> {
    	private String url = null;
    	private String title = null;
    	private String isbn = null;
    	private String price = null;
    	private int nBookstore;
    	private HttpClient httpclient = null;
    	private HttpGet httpget = null;
    	private boolean bHttpConnectSuccess = false;

    	private String decode(String str, char unknownCh) {
            StringBuffer sb = new StringBuffer();
            int i1=0;
            int i2=0;

            while(i2<str.length()) {
                i1 = str.indexOf("&#",i2);
                if (i1 == -1 ) {
                     sb.append(str.substring(i2));
                     break ;
                }
                sb.append(str.substring(i2, i1));
                i2 = str.indexOf(";", i1);
                if (i2 == -1 ) {
                     sb.append(str.substring(i1));
                     break ;
                }

                String tok = str.substring(i1+2, i2);
                try {
                     int radix = 10 ;
                     if (tok.charAt(0) == 'x' || tok.charAt(0) == 'X') {
                         radix = 16 ;
                         tok = tok.substring(1);
                     }
                     sb.append((char) Integer.parseInt(tok, radix));
                } catch (NumberFormatException exp) {
                     sb.append(unknownCh);
                }
                i2++ ;
            }
            return sb.toString();
    	}
             
         private String retriveWebPage(String url, String encoding) {
    		if (url == null) {
    			return null;
    		}
    		
    		if (url.length() == 0) {
    			return null;
    		}
    		
    		httpget = new HttpGet(url); 
            String responseBody = null;

            try {
            	HttpResponse response = httpclient.execute(httpget);
            	StatusLine statusLine = response.getStatusLine();
    			int statusCode = statusLine.getStatusCode();
    			if (statusCode == HttpStatus.SC_OK) {
    	        	HttpEntity entity = response.getEntity();
    	        	if (entity != null) {
    	        		responseBody = EntityUtils.toString(entity, encoding);
    					entity.consumeContent();
    					bHttpConnectSuccess = true;
    	        	}
    			}
    		} catch (ClientProtocolException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            
            return responseBody;
        }
         
        private String parseRealURLFromSanmin(String webpage) {
    		if (webpage == null) {
    			return null;
    		}
    		
    		if (webpage.length() == 0) {
    			return null;
    		}

    		String real_url = null;
    		int start = webpage.indexOf("QueryBookNmSort('");
    		int end = webpage.indexOf("');", start);
    		if (start != -1 && end != -1) {
    			real_url = String.format(URL_SANMIN_BOOK, webpage.substring(start + 17, end));
    		}
    		
    		return real_url;
    	}

        private static final String FINDBOOK_TITLE_START_TAG = "<span style=\"text-decoration:underline\">";
        private static final String FINDBOOK_TITLE_END_TAG = "</span>";
        
        private boolean parseInfoFromFindbook(String webpage) {
    		if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}
    		
    		int start_1 = webpage.indexOf(FINDBOOK_TITLE_START_TAG);
    		int end_1 = webpage.indexOf(FINDBOOK_TITLE_END_TAG, start_1);
    		if (start_1 != -1 && end_1 != -1) {
    			title = webpage.substring(start_1 + FINDBOOK_TITLE_START_TAG.length(), end_1);
    		}
    		
    		if (title == null) {
    			return false;
    		}
    		
    		return true;
        }
         
        private static final String SANMIN_URL_START_TAG = "page-product.asp?pid=";
        private static final String SANMIN_URL_END_TAG = "\">";
        private static final String SANMIN_TITLE_END_TAG = "</a>";
        private static final String SANMIN_PRICE_START_TAG = "<span class=\"price\">";
        private static final String SANMIN_PRICE_END_TAG = "</span>元";
        
    	private boolean parseInfoFromSanmin(String webpage) {
    		if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

    		int start_2 = webpage.indexOf(SANMIN_URL_START_TAG);
    		int end_2 = webpage.indexOf(SANMIN_URL_END_TAG, start_2);
    		if (start_2 != -1 && end_2 != -1) {
    			url = "http://www.sanmin.com.tw/" + webpage.substring(start_2, end_2);
    		}
    		
    		int start_3 = (end_2 == -1 ? -1 : (end_2 + SANMIN_URL_END_TAG.length()));
    		int end_3 = webpage.indexOf(SANMIN_TITLE_END_TAG, start_3);
    		if (start_3 != -1 && end_3 != -1) {
    			title = webpage.substring(start_3, end_3);
    		}
    		
    		int start_4 = webpage.indexOf(SANMIN_PRICE_START_TAG, end_3);
    		int end_4 = webpage.indexOf(SANMIN_PRICE_END_TAG, start_4);
    		if (start_4 != -1 && end_4 != -1) {
    			// find next tag
    			int pos_test = webpage.indexOf(SANMIN_PRICE_START_TAG, start_4 + 1);
    			if (pos_test != -1) {
    				start_4 = pos_test;
    			}
    			price = webpage.substring(start_4 + SANMIN_PRICE_START_TAG.length(), end_4);
    		}
    		
    		if (title != null && url != null) {
    			Log.d(TAG, "Title:" + title + ", url:" + url);
    			return true;
    		}
    		
    		return false;
    	}

        private static final String ESLITE_URL_START_TAG = "tn15";
        private static final String ESLITE_URL_START_TAG_2 = "<a href=\"http://";
        private static final String ESLITE_URL_END_TAG = "\">";
        private static final String ESLITE_TITLE_END_TAG = "</a>";
        private static final String ESLITE_PRICE_START_TAG = "NT$<span class=\"price_sale\">";
        private static final String ESLITE_PRICE_END_TAG = "元</span>";
        
    	private boolean parseInfoFromEslite(String webpage) {
    		if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

    		int start_1 = webpage.indexOf(ESLITE_URL_START_TAG);
    		int start_2 = webpage.indexOf(ESLITE_URL_START_TAG_2, start_1);
    		int end_2 = webpage.indexOf(ESLITE_URL_END_TAG, start_2);
    		if (start_2 != -1 && end_2 != -1) {
    			url = webpage.substring(start_2 + 9, end_2); // 9 is length of "<a href=\""
    		}
    		
    		int start_3 = (end_2 == -1 ? -1 : (end_2 + ESLITE_URL_END_TAG.length()));
    		int end_3 = webpage.indexOf(ESLITE_TITLE_END_TAG, start_3);
    		if (start_3 != -1 && end_3 != -1) {
    			title = webpage.substring(start_3, end_3).trim();
    			title = title.substring(0, title.length() - 1);
    		}
    		
    		int start_4 = webpage.indexOf(ESLITE_PRICE_START_TAG, end_3);
    		int end_4 = webpage.indexOf(ESLITE_PRICE_END_TAG, start_4);
    		if (start_4 != -1 && end_4 != -1) {
    			price = webpage.substring(start_4 + ESLITE_PRICE_START_TAG.length(), end_4);
    		}
    		
    		if (title != null && url != null) {
    			Log.d(TAG, "Title:" + title + ", url:" + url);
    			return true;
    		}
    		
    		return false;
    	}
    	
        private static final String KINGSTONE_URL_START_TAG = "bc-info";
        private static final String KINGSTONE_URL_START_TAG_2 = "<h1><a href=\"";
        private static final String KINGSTONE_URL_END_TAG = "\">";
        private static final String KINGSTONE_TITLE_END_TAG = "</a>";
        private static final String KINGSTONE_PRICE_START_TAG = "<span class=\"price\">";
        private static final String KINGSTONE_PRICE_END_TAG = "</span>";
        
    	private boolean parseInfoFromKingstone(String webpage) {
    		if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

    		int start_1 = webpage.indexOf(KINGSTONE_URL_START_TAG);
    		int start_2 = webpage.indexOf(KINGSTONE_URL_START_TAG_2, start_1);
    		int end_2 = webpage.indexOf(KINGSTONE_URL_END_TAG, start_2);
    		if (start_2 != -1 && end_2 != -1) {
    			url = webpage.substring(start_2 + KINGSTONE_URL_START_TAG_2.length(), end_2);
    		}
    		
    		int start_3 = (end_2 == -1 ? -1 : (end_2 + KINGSTONE_URL_END_TAG.length()));
    		int end_3 = webpage.indexOf(KINGSTONE_TITLE_END_TAG, start_3);
    		if (start_3 != -1 && end_3 != -1) {
    			title = decode(webpage.substring(start_3, end_3), ' ');
    		}
    		
    		int start_4 = webpage.indexOf(KINGSTONE_PRICE_START_TAG, end_3);
    		int end_4 = webpage.indexOf(KINGSTONE_PRICE_END_TAG, start_4);
    		if (start_4 != -1 && end_4 != -1) {
    			price = webpage.substring(start_4 + KINGSTONE_PRICE_START_TAG.length(), end_4);
    		}
    		
    		if (title != null && url != null) {
    			Log.d(TAG, "Title:" + title + ", url:" + url);
    			return true;
    		}
    		
    		return false;
    	}
    	
        private static final String BOOKS_URL_START_TAG = "mid_image";
        private static final String BOOKS_URL_START_TAG_2 = "item=";
        private static final String BOOKS_URL_END_TAG = "\"";
        private static final String BOOKS_TITLE_START_TAG = "title=\"";
        private static final String BOOKS_TITLE_END_TAG = "\">";
        private static final String BOOKS_PRICE_START_TAG = "優惠價：<strong><b>";
        private static final String BOOKS_PRICE_START_TAG_2 = "優惠價：<b>";
        private static final String BOOKS_PRICE_START_TAG_3 = "<b>";
        private static final String BOOKS_PRICE_END_TAG = "</b>元</strong>";
        
    	private boolean parseInfoFromBooks(String webpage) {
    		if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

    		int start_1 = webpage.indexOf(BOOKS_URL_START_TAG);
    		int start_2 = webpage.indexOf(BOOKS_URL_START_TAG_2, start_1);
    		int end_2 = webpage.indexOf(BOOKS_URL_END_TAG, start_2);
    		if (start_2 != -1 && end_2 != -1) {
    			String itemId = webpage.substring(start_2 + BOOKS_URL_START_TAG_2.length(), end_2);
    			url = String.format(URL_BOOKS_BOOK, itemId);
    		}
    		
    		int start_3 = webpage.indexOf(BOOKS_TITLE_START_TAG, start_1);
    		int end_3 = webpage.indexOf(BOOKS_TITLE_END_TAG, start_3);
    		if (start_3 != -1 && end_3 != -1) {
    			title = webpage.substring(start_3 + BOOKS_TITLE_START_TAG.length(), end_3);
    		}
    		
    		int shift = BOOKS_PRICE_START_TAG.length();
    		int start_4 = webpage.indexOf(BOOKS_PRICE_START_TAG, end_3);
    		if (start_4 == -1) {
    			shift = BOOKS_PRICE_START_TAG_2.length();
    			start_4 = webpage.indexOf(BOOKS_PRICE_START_TAG_2, end_3);
    		}
    		int end_4 = webpage.indexOf(BOOKS_PRICE_END_TAG, start_4);
    		if (start_4 != -1 && end_4 != -1) {
    			int pos_test = webpage.indexOf(BOOKS_PRICE_START_TAG_3, start_4 + shift);
    			if (pos_test != -1 && pos_test < end_4) {
    				start_4 = pos_test + BOOKS_PRICE_START_TAG_3.length();
    			} else {
    				start_4 = start_4 + shift;
    			}
    			price = webpage.substring(start_4, end_4);
    		}
    		
    		if (title != null && url != null) {
    			Log.d(TAG, "Title:" + title + ", url:" + url);
    			return true;
    		}
    		
    		return false;
    	}
    	
    	@Override
		protected Boolean doInBackground(String... params) {
			isbn = params[0];
			nBookstore = Integer.parseInt(params[1]);
			String url_store = null;
			String webpage = null;
			boolean bProcess = false;

			switch (nBookstore) {
			default:
			case BOOKSTORE_FINDBOOK:
				Log.d(TAG, "Findbook: ");
				url_store = String.format(URL_FINDBOOK, isbn); 
				webpage = retriveWebPage(url_store, "UTF-8");		
				bProcess = parseInfoFromFindbook(webpage);
				break;
				
			case BOOKSTORE_BOOKS:		
				Log.d(TAG, "Books: ");
				url_store = String.format(URL_BOOKS, isbn); 
				webpage = retriveWebPage(url_store, "Big5");
				bProcess = parseInfoFromBooks(webpage);
				break;
				
			case BOOKSTORE_ESLITE:		
				Log.d(TAG, "Eslite: ");
				url_store = String.format(URL_ESLITE, isbn); 
				webpage = retriveWebPage(url_store, "UTF-8");	
				bProcess = parseInfoFromEslite(webpage);
				break;
				
			case BOOKSTORE_SANMIN:	
				Log.d(TAG, "Sanmin: ");
				url_store = String.format(URL_SANMIN, isbn); 
				webpage = retriveWebPage(url_store, "UTF-8");
				url_store = parseRealURLFromSanmin(webpage);
				if (url_store != null) {
					Log.d(TAG, "url:" + url_store);
					webpage = retriveWebPage(url_store, "UTF-8");	
					bProcess = parseInfoFromSanmin(webpage);
				}
				break;
				
			case BOOKSTORE_KINGSTONE:	
				Log.d(TAG, "Kingstone: ");
				url_store = String.format(URL_KINGSTONE, isbn); 
				webpage = retriveWebPage(url_store, "UTF-8");			
				bProcess = parseInfoFromKingstone(webpage);
				break;
				
			case BOOKSTORE_TENLONG:		
				Log.d(TAG, "Tenlong: ");
				if (isbn.length() > 9) {
					url_store = String.format(URL_TENLONG, isbn.substring(3, isbn.length() - 1));
				} else {
					url_store = String.format(URL_TENLONG, isbn);
				}
				webpage = retriveWebPage(url_store, "UTF-8");			
				break;
			}
			
			return bProcess;
		}
		
    	protected void onPreExecute() {
    		httpclient = new DefaultHttpClient();
    		httpclient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 10000);
    		httpclient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000);
    	}

    	protected void onPostExecute(Boolean isSuccess) {
            httpclient.getConnectionManager().shutdown();        
            mFetchWebPageTask = null;
			dismissProgressBar();
			if (isSuccess == true) {
				mAdapter.updateBooks(isbn, nBookstore, title, url, price);
			} else {
				if (bHttpConnectSuccess) {
		            new AlertDialog.Builder(FindBooks.this)
	            	.setTitle(R.string.error_text)
	            	.setMessage(R.string.not_found_text)
	            	.show();
		            mAdapter.removeBook(isbn, nBookstore);
				} else {
					if (title != null) {
						mAdapter.updateBooks(isbn, nBookstore, title, url, price);
					}
				}
			}
			
			if (bHttpConnectSuccess) {
				mAdapter.updateBooks(isbn, nBookstore, title, url, price);
			}
		}
		
		protected void onCancelled() {
			httpget.abort();
			httpclient.getConnectionManager().shutdown();
		}
    }
    
    private void goFetchWebPageTask(String isbn, String store) {
    	if (isbn == null || store == null) {
    		return;
    	}
    	
    	if (isbn.length() == 0 || store.length() == 0) {
    		return;
    	}
    	
    	displayProgressBar(R.string.loading_text);
		try {
			mFetchWebPageTask = new FetchWebPageTask();
			mFetchWebPageTask.execute(isbn, store);
		} catch(RejectedExecutionException e) {
			dismissProgressBar();
			e.printStackTrace();
		}
    }
    
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
	
	@Override
	protected void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		if (mFetchWebPageTask != null) {
			mFetchWebPageTask.cancel(true);
			mFetchWebPageTask = null;
		}
		dismissProgressBar();
		super.onPause();
	}

	@Override
	protected void onResume() {
		Log.d(TAG, "onResume:" + mResumeTask);
		if (mResumeTask) {
			goFetchWebPageTask(mISBN, mStore + "");
		}
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "onStop");
		super.onStop();
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.d(TAG, "onRestoreInstanceState");
		if (savedInstanceState != null) {
			mISBN = savedInstanceState.getString("mISBN");
			mStore = savedInstanceState.getInt("mStore");
			mResumeTask = savedInstanceState.getBoolean("mResumeTask");
		}
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(TAG, "onSaveInstanceState");
		if (mFetchWebPageTask != null) {
			outState.putBoolean("mResumeTask", true);
		}
		outState.putString("mISBN", mISBN);
		outState.putInt("mStore", mStore);
		super.onSaveInstanceState(outState);
	}

	private class SavedObject {
		public BooksAdapter mAdapter = null;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		SavedObject savedObject = new SavedObject();
		savedObject.mAdapter = mAdapter;
		
		return savedObject;
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
        	SavedObject savedObject = (SavedObject) getLastNonConfigurationInstance();
        	mAdapter = savedObject.mAdapter;
        }
        
        setContentView(R.layout.main);
       
        if (mSpinner == null) {
	        mSpinner = (Spinner) findViewById(R.id.spinner_store);
		    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
		    		this, R.array.stores, android.R.layout.simple_spinner_item);
		    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		    mSpinner.setAdapter(adapter);
        }
        
        if (mEditText == null) {
	        mEditText = (EditText) findViewById(R.id.edit_isbn);
	        mEditText.addTextChangedListener(mTextWatcher);
        }
        
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
					
					/* currently in ISBN-13 mode */
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
				int nService = mSpinner.getSelectedItemPosition() + 1;
	    		FindbookByISBN_Advance(isbn, nService);
			}}
        );
        
        if (mAdapter == null) {
	        mAdapter = new BooksAdapter(this);
        }
        
        ListView listview = (ListView) findViewById(R.id.list_isbn);        
        listview.setAdapter(mAdapter);
        listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
				SearchItems book = (SearchItems) parent.getAdapter().getItem(pos);
				if (book == null) {
					return;
				}
				
				String isbn = book.isbn;
				if (isbn == null) {
					return;
				}
				
				mEditText.setText(isbn);
    			mEditText.setSelection(isbn.length(), isbn.length());

    			mSpinner.setSelection(book.bookstore - 1);
    			
    			if (book.url != null) {
    				Intent i = new Intent(Intent.ACTION_VIEW);
    				i.setData(Uri.parse(book.url));
    				startActivity(i);
    			} else {
    				FindbookByISBN(isbn, book.bookstore);
    			}
			}
        });        
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
    		int nService = mSpinner.getSelectedItemPosition() + 1;
    		FindbookByISBN_Advance(isbn, nService);
    	} else {
    		Log.d(TAG, "Nothing found..");
    	}
    }

    private void FindbookByISBN(String isbn, int nBookstore) {
    	if (isbn == null) {
    		return;
    	}
    	
    	if (isbn.length() == 0) {
    		return;
    	}
    	Log.d(TAG, "isbn:" + isbn);
		String url_store = null;

		switch (nBookstore) {
		default:
		case BOOKSTORE_FINDBOOK:	url_store = String.format(URL_FINDBOOK, isbn); break;
		case BOOKSTORE_BOOKS:		url_store = String.format(URL_BOOKS, isbn); break;
		case BOOKSTORE_ESLITE:		url_store = String.format(URL_ESLITE, isbn); break;
		case BOOKSTORE_SANMIN:		url_store = String.format(URL_SANMIN, isbn); break;
		case BOOKSTORE_KINGSTONE:	url_store = String.format(URL_KINGSTONE, isbn); break;
		case BOOKSTORE_TENLONG:		
			if (isbn.length() > 9) {
				url_store = String.format(URL_TENLONG, isbn.substring(3, isbn.length() - 1));
			} else {
				url_store = String.format(URL_TENLONG, isbn);
			}
			break;
		}

		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url_store));
		startActivity(i);
    }

    private void FindbookByISBN_Advance(String isbn, int nBookstore) {
    	if (isbn == null) {
    		return;
    	}
    	
    	if (isbn.length() == 0) {
    		return;
    	}
    	Log.d(TAG, "isbn:" + isbn);
    	
    	mISBN = isbn;
    	mStore = nBookstore;

		goFetchWebPageTask(mISBN, mStore + "");
		mAdapter.addBooks(mISBN, mStore);
    }
    
    public class BooksAdapter extends BaseAdapter {
    	private LayoutInflater mInflater;
    	private ArrayList<SearchItems> mBooks;
    	String mBookStores[];
        
        public BooksAdapter(Context c) {
            mInflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mBookStores = c.getResources().getStringArray(R.array.stores);
            mBooks = new ArrayList<SearchItems>();
        }

        public int getCount() {
            return mBooks.size();
        }

        public Object getItem(int position) {
            return mBooks.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
        	ViewHolder holder;
        	if (convertView == null) {
        		convertView = mInflater.inflate(R.layout.searchitem, parent, false);
        		holder = new ViewHolder();
        		holder.isbn = (TextView) convertView.findViewById(R.id.text_isbn);
        		holder.store = (TextView) convertView.findViewById(R.id.text_store);
        		holder.price = (TextView) convertView.findViewById(R.id.text_price);
        		
        		convertView.setTag(holder);
        	} else {
        		holder = (ViewHolder) convertView.getTag();
        	}

        	SearchItems book = mBooks.get(position);
        	if (book.name != null) {
        		holder.isbn.setText(book.name);
        	} else {
        		holder.isbn.setText(book.isbn);
        	}
        	holder.store.setText(mBookStores[book.bookstore - 1]);
        	if (book.price != null) {
        		holder.price.setText(getResources().getString(R.string.text_price_text, book.price));
        	} else {
        		holder.price.setText(null);
        	}

            return convertView;
        }

        public void clearBooks() {
        	mBooks.clear();
            notifyDataSetChanged();
        }
        
        public void updateBooks(String isbn, int bookstore, String name, String url, String price) {
        	for (SearchItems si : mBooks) {
        		if (si.isbn.equals(isbn) && si.bookstore == bookstore) {
        			si.name = name;
        			si.url = url;
        			si.price = price;
        			break;
        		}
        	}
            notifyDataSetChanged();
        }
        
        public void addBooks(String isbn, int bookstore) {
        	for (SearchItems si : mBooks) {
        		if (si.isbn.equals(isbn) && si.bookstore == bookstore) {
        			mBooks.remove(si);
        			break;
        		}
        	}
        	
        	SearchItems input = new SearchItems();
        	input.isbn = isbn;
        	input.bookstore = bookstore;
            mBooks.add(0, input);
            notifyDataSetChanged();
        }
        
        public void removeBook(String isbn, int bookstore) {
        	SearchItems book = null;
        	if (isbn == null) {
        		return;
        	}
        	
        	if (isbn.length() == 0) {
        		return;
        	}
        	
        	if (mBooks == null) {
        		return;
        	}
        	
        	book = mBooks.get(0);
        	if (book == null) {
        		return;
        	}
        	
        	if (isbn.equals(book.isbn) && bookstore == book.bookstore) {
        		mBooks.remove(0);
        		return;
        	}
        	
        	for (SearchItems si : mBooks) {
        		if (si.isbn.equals(isbn) && si.bookstore == bookstore) {
        			mBooks.remove(si);
        		}
        	}
        }
    }    
}
