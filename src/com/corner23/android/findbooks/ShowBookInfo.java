package com.corner23.android.findbooks;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class ShowBookInfo extends Activity {
	private static final String TAG = "FindBooks";
	
	private static final int BOOKSTORE_START = 1;
	private static final int BOOKSTORE_FINDBOOK = BOOKSTORE_START;
	private static final int BOOKSTORE_ESLITE = 2;
	private static final int BOOKSTORE_BOOKS = 3;
	private static final int BOOKSTORE_SANMIN = 4;
	private static final int BOOKSTORE_KINGSTONE = 5;
	private static final int BOOKSTORE_TENLONG = 6;
	private static final int BOOKSTORE_END = BOOKSTORE_TENLONG + 1;
	
	private static final String URL_FINDBOOK = "http://findbook.tw/m/book/%s/price";
	private static final String URL_ESLITE = "http://www.eslite.com/Search_BW.aspx?query=%s";
	private static final String URL_BOOKS = "http://search.books.com.tw/exep/prod_search.php?key=%s";
	private static final String URL_SANMIN = "http://www.sanmin.com.tw/page-qsearch.asp?qu=%s";
	private static final String URL_KINGSTONE = "http://search.kingstone.com.tw/SearchResult.asp?c_name=%s";
	private static final String URL_TENLONG = "http://www.tenlong.com.tw/search?keyword=%s";
		// "http://tlsj.tenlong.com.tw/WebModule/BookSearch/bookSearchAction.do?fkeyword=%s";
		// "http://tlsj.tenlong.com.tw/WebModule/BookSearch/bookSearchAdvancedAction.do?bookname=&submit=Submit&author=&isbn=%s&publisher_id=&chinese=&pub_date=&fpub_date_year=&book_order=";
	
	private static final String URL_TENLONG_BOOK = "http://www.tenlong.com.tw/items/%s";
	private static final String URL_BOOKS_BOOK = "http://www.books.com.tw/exep/prod/booksfile.php?item=%s";
	private static final String URL_SANMIN_BOOK = "http://www.sanmin.com.tw/QueryBookNmSort.asp?%s";
	
	private static final int TIMEOUT = 10000;
	
	private BooksAdapter mAdapter = null;
	private ProgressDialog mProgressDialog = null;
	private ImageView mCoverImageView = null;
	private TextView mTitleTextView = null;
	private TextView mPriceTextView = null;

	private boolean mResumeFetchWebPageTask = false;
	private boolean mResumeFetchBookInfoTask = false;
	private String mISBN = null;
	private int mCurrentStore = BOOKSTORE_START;
	private String mPrice = null;
	private String mTitle = null;
	private Bitmap mCover = null;
	private int mStores = 0;
	
	private boolean bUseSystemProxy = true;
	
	private HttpClient httpclient = null;
	private HttpGet httpget = null;
	
	private FetchWebPageTask mFetchWebPageTask = null;
	private FetchBookInfoTask mFetchBookInfoTask = null;
	
	static class ViewHolder {
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
					if (mFetchBookInfoTask != null) {
						mFetchBookInfoTask.cancel(true);
						mFetchBookInfoTask = null;
					}
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
	
	private void initHttpClient() {
		if (httpclient == null) {
			httpclient = new DefaultHttpClient();
			httpclient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, TIMEOUT);
			httpclient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, TIMEOUT);    		
		}
	}
	
	private void shutdownHttpClient() {
		httpclient.getConnectionManager().shutdown();
		httpclient = null;
	}

	private class FetchBookInfoTask extends AsyncTask<String, Void, Boolean> {
		private String isbn = null;

        private String retriveWebPage(String url, String encoding) {
    		if (url == null) {
    			return null;
    		}
    		
    		if (url.length() == 0) {
    			return null;
    		}
    		
    		initHttpClient();
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
    	        	}
    			}
    		} catch (ClientProtocolException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            return responseBody;
        }

        private static final String TITLE_FINDBOOK_START_TAG = "<span style=\"text-decoration:underline\">";
        private static final String TITLE_FINDBOOK_END_TAG = "</span>";
        private static final String PRICE_FINDBOOK_START_TAG = "定價:";
        private static final String PRICE_FINDBOOK_END_TAG = "</span>";
        private static final String COVER_FINDBOOK_URL = "http://static.findbook.tw/image/book/%s/large";
		
        private boolean retrieveCoverFromFindbook() {
			String url_store = String.format(URL_FINDBOOK, isbn);
			String webpage = retriveWebPage(url_store, "UTF-8");
			if (webpage == null) {
				return false;
			}
			
    		if (webpage.length() == 0) {
    			return false;
    		}

    		try {
				URL url = new URL(String.format(COVER_FINDBOOK_URL, isbn));
				mCover = BitmapCache.getInstance(bUseSystemProxy).load(url);
				if (mCover != null) {
					return true;
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			
			return false;
        }
        
        private boolean retrieveTitlePriceFromFindbook() {
			String url_store = String.format(URL_FINDBOOK, isbn);
			String webpage = retriveWebPage(url_store, "UTF-8");
			if (webpage == null) {
				return false;
			}
			
    		if (webpage.length() == 0) {
    			return false;
    		}
			int start_1 = webpage.indexOf(TITLE_FINDBOOK_START_TAG);
			int end_1 = webpage.indexOf(TITLE_FINDBOOK_END_TAG, start_1);
			if (start_1 != -1 && end_1 != -1) {
				mTitle = decode(webpage.substring(start_1 + TITLE_FINDBOOK_START_TAG.length(), end_1), ' ');
			}
						
			int start_2 = webpage.indexOf(PRICE_FINDBOOK_START_TAG);
			int end_2 = webpage.indexOf(PRICE_FINDBOOK_END_TAG, start_2);
			if (start_2 != -1 && end_2 != -1) {
				mPrice = webpage.substring(start_2 + PRICE_FINDBOOK_START_TAG.length(), end_2);
			}
				
			if (mTitle != null && mPrice != null) {
				return true;
			}

			return false;
        }
        
        private static final String BOOKS_URL_START_TAG = "mid_image";
        private static final String COVER_BOOKS_START_TAG = "http://www.books.com.tw/exep/lib/image.php?image=";
        private static final String COVER_BOOKS_END_TAG = "&amp;width=";
        private static final String TITLE_BOOKS_START_TAG = "title=\"";
        private static final String TITLE_BOOKS_END_TAG = "\">";
        private static final String PRICE_BOOKS_START_TAG = "原價：<s>";
        private static final String PRICE_BOOKS_END_TAG = "元</s>";
        
    	private boolean retrieveCoverFromBooks() {
			String url_store = String.format(URL_BOOKS, isbn); 
			String webpage = retriveWebPage(url_store, "Big5");
			if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

			int start_1 = webpage.indexOf(COVER_BOOKS_START_TAG);
			int end_1 = webpage.indexOf(COVER_BOOKS_END_TAG, start_1);
			if (start_1 != -1 && end_1 != -1) {
				try {
			        URL url = new URL(webpage.substring(start_1 + COVER_BOOKS_START_TAG.length(), end_1));
					mCover = BitmapCache.getInstance(bUseSystemProxy).load(url);
					if (mCover != null) {
						return true;
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			
			return false;
    	}
    	
    	private boolean retrieveTitlePriceFromBooks() {
			String url_store = String.format(URL_BOOKS, isbn); 
			String webpage = retriveWebPage(url_store, "Big5");
			if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

    		int start_2 = webpage.indexOf(BOOKS_URL_START_TAG);
			if (start_2 == -1) {
				return false;
			}
    		int start_3 = webpage.indexOf(TITLE_BOOKS_START_TAG, start_2);
    		int end_3 = webpage.indexOf(TITLE_BOOKS_END_TAG, start_3);
    		if (start_3 != -1 && end_3 != -1) {
    			mTitle = webpage.substring(start_3 + TITLE_BOOKS_START_TAG.length(), end_3);
    		}
    		
    		int start_4 = webpage.indexOf(PRICE_BOOKS_START_TAG, end_3);
    		int end_4 = webpage.indexOf(PRICE_BOOKS_END_TAG, start_4);
    		if (start_4 != -1 && end_4 != -1) {
    			mPrice = webpage.substring(start_4 + PRICE_BOOKS_START_TAG.length(), end_4);
    		}
    		
    		if (mTitle != null && mPrice != null) {
    			return true;
    		}
    		
    		return false;
    	}
    	        
		@Override
		protected Boolean doInBackground(String... arg0) {
			isbn = arg0[0];
			if (retrieveCoverFromFindbook() == false) {
				retrieveCoverFromBooks();
			}
			
			if (retrieveTitlePriceFromFindbook() == false) {
				retrieveTitlePriceFromBooks();
			}
			
			return true;
		}
		
    	protected void onPostExecute(Boolean bSuccess) {
			dismissProgressBar();
			mFetchBookInfoTask = null;
			if (mCoverImageView != null && mCover != null) {
				mCoverImageView.setImageBitmap(mCover);
			}
			
			if (mTitleTextView != null && mTitle != null) {
				mTitleTextView.setText(mTitle);
			}
			
			if (mPriceTextView != null && mPrice != null) {
	        	mPriceTextView.setText(getResources().getString(R.string.text_price_text, mPrice));
			}
			
			goFetchWebPageTask(isbn);
		}
		
		protected void onCancelled() {
			dismissProgressBar();
			mFetchBookInfoTask = null;
			if (httpget != null) {
				httpget.abort();
			}
		}
	}
	
    private void goFetchBookInfoTask(String isbn) {
    	if (isbn == null) {
    		return;
    	}
    	
    	if (isbn.length() == 0) {
    		return;
    	}
    	
    	displayProgressBar(R.string.loading_book_info_text);
		try {
			mFetchBookInfoTask = new FetchBookInfoTask();
			mFetchBookInfoTask.execute(isbn);
		} catch(RejectedExecutionException e) {
			dismissProgressBar();
			e.printStackTrace();
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
    	private boolean bHttpConnectSuccess = false;
             
        private String retriveWebPage(String url, String encoding) {
    		if (url == null) {
    			return null;
    		}
    		
    		if (url.length() == 0) {
    			return null;
    		}
    		
        	initHttpClient();
    		httpget = new HttpGet(url); 
            String responseBody = null;

            try {
            	HttpResponse response = httpclient.execute(httpget);
            	StatusLine statusLine = response.getStatusLine();
    			int statusCode = statusLine.getStatusCode();
				bHttpConnectSuccess = true;
    			if (statusCode == HttpStatus.SC_OK) {
    	        	HttpEntity entity = response.getEntity();
    	        	if (entity != null) {
    	        		responseBody = EntityUtils.toString(entity, encoding);
    					entity.consumeContent();
    	        	}
    			}
    		} catch (ClientProtocolException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            
            return responseBody;
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

        private static final String TENLONG_URL_START_TAG = "items-table";
        private static final String TENLONG_URL_START_TAG_2 = "title=\"";
        private static final String TENLONG_URL_END_TAG = "\">";
        private static final String TENLONG_PRICE_START_TAG = "<span class=\"pricing\">";
        private static final String TENLONG_PRICE_START_TAG_2 = "$";
        private static final String TENLONG_PRICE_END_TAG = "</span>";
        
    	private boolean parseInfoFromTenlong(String webpage) {
    		if (webpage == null) {
    			return false;
    		}
    		
    		if (webpage.length() == 0) {
    			return false;
    		}

    		url = String.format(URL_TENLONG_BOOK, isbn);
    		
    		int start_1 = webpage.indexOf(TENLONG_URL_START_TAG);
    		int start_2 = webpage.indexOf(TENLONG_URL_START_TAG_2, start_1);
    		start_2 = webpage.indexOf(TENLONG_URL_START_TAG_2, start_2 + 1);
    		int end_2 = webpage.indexOf(TENLONG_URL_END_TAG, start_2);
    		if (start_2 != -1 && end_2 != -1) {
    			title = webpage.substring(start_2 + TENLONG_URL_START_TAG_2.length(), end_2);
    		}
    		
    		int start_3 = webpage.indexOf(TENLONG_PRICE_START_TAG);
    		int start_4 = webpage.indexOf(TENLONG_PRICE_START_TAG_2, start_3);
    		start_4 = webpage.indexOf(TENLONG_PRICE_START_TAG_2, start_4 + 1);
    		int end_4 = webpage.indexOf(TENLONG_PRICE_END_TAG, start_4);
    		if (start_4 != -1 && end_4 != -1) {
    			price = webpage.substring(start_4 + TENLONG_PRICE_START_TAG_2.length(), end_4);
    		}
    		
    		if (title != null && url != null) {
    			Log.d(TAG, "Title:" + title + ", url:" + url);
    			return true;
    		}
    		
    		return false;
    	}
    	    	
        private static final String ESLITE_URL_START_TAG = "ctl00_ContentPlaceHolder1_rptProducts_ctl00_imgBookCover";
        private static final String ESLITE_URL_START_TAG_2 = "http://www.eslite.com/product.aspx?pgid=";
        private static final String ESLITE_URL_END_TAG = "\">";
        private static final String ESLITE_TITLE_START_TAG = "ctl00_ContentPlaceHolder1_rptProducts_ctl00_LblName\">";
        private static final String ESLITE_TITLE_END_TAG = "</span>";
        private static final String ESLITE_PRICE_START_TAG = "NT$<span";
        private static final String ESLITE_PRICE_START_TAG_2 = "class=\"price_sale\">";
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
    			url = webpage.substring(start_2, end_2);
    		}
    		
    		int start_3 = webpage.indexOf(ESLITE_TITLE_START_TAG, end_2);
    		int end_3 = webpage.indexOf(ESLITE_TITLE_END_TAG, start_3);
    		if (start_3 != -1 && end_3 != -1) {
    			title = webpage.substring(start_3 + ESLITE_TITLE_START_TAG.length(), end_3);
    		}
    		
    		int start_4_1 = webpage.indexOf(ESLITE_PRICE_START_TAG, end_3);
    		int start_4_2 = webpage.indexOf(ESLITE_PRICE_START_TAG_2, start_4_1);
    		int end_4 = webpage.indexOf(ESLITE_PRICE_END_TAG, start_4_2);
    		if (start_4_2 != -1 && end_4 != -1) {
    			price = webpage.substring(start_4_2 + ESLITE_PRICE_START_TAG_2.length(), end_4);
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
			String url_store = null;
			String webpage = null;
			boolean bProcess = false;

			while (mCurrentStore < BOOKSTORE_END) {
				int shift = mCurrentStore - BOOKSTORE_START;
				if (((mStores >> shift) & 1) == 1) {
					break;
				}
				mCurrentStore++;
			}
			
			Log.d(TAG, "store:" + mCurrentStore);
			
			if (mCurrentStore == BOOKSTORE_END) {
				return false;
			}
			
			switch (mCurrentStore) {
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
				webpage = retriveWebPage(url_store, "UTF-8");
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
				url_store = String.format(URL_TENLONG, isbn);
				webpage = retriveWebPage(url_store, "UTF-8");			
				bProcess = parseInfoFromTenlong(webpage);
				break;
			}
			
			return bProcess;
		}
		
    	protected void onPostExecute(Boolean isSuccess) {
            mFetchWebPageTask = null;
			if (mCurrentStore >= BOOKSTORE_END) {
				dismissProgressBar();
			} else {
				if (isSuccess != true) {
					if (bHttpConnectSuccess) {
						title = getResources().getString(R.string.not_found_text);
					}
				}
				
				if (title != null) {
					mAdapter.addBooks(isbn, mCurrentStore, title, url, price);
				}
	
				mCurrentStore++;
				goFetchWebPageTask(isbn);
			}
		}
		
		protected void onCancelled() {
			dismissProgressBar();
			mFetchWebPageTask = null;
			if (httpget != null) {
				httpget.abort();
			}
		}
    }
    
    private void goFetchWebPageTask(String isbn) {
    	if (isbn == null) {
    		return;
    	}
    	
    	if (isbn.length() == 0) {
    		return;
    	}
    	
    	displayProgressBar(R.string.loading_price_info_text);
		try {
			mFetchWebPageTask = new FetchWebPageTask();
			mFetchWebPageTask.execute(isbn);
		} catch(RejectedExecutionException e) {
			dismissProgressBar();
			e.printStackTrace();
		}
    }

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
		if (mFetchBookInfoTask != null) {
			mFetchBookInfoTask.cancel(true);
			mFetchBookInfoTask = null;
		}
		shutdownHttpClient();
		dismissProgressBar();
		super.onPause();
	}

	@Override
	protected void onResume() {
		Log.d(TAG, "onResume:");
		
		initHttpClient();
		
		if (mResumeFetchBookInfoTask) {
			goFetchBookInfoTask(mISBN);
		} else if (mResumeFetchWebPageTask) {
			goFetchWebPageTask(mISBN);
		}

		try {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm != null) {
				NetworkInfo ni = cm.getActiveNetworkInfo();
				if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
					bUseSystemProxy = false;
				}
			}
		} catch (Exception e) {
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
			mStores = savedInstanceState.getInt("mStores");
			mCurrentStore = savedInstanceState.getInt("mCurrentStore");
			mResumeFetchWebPageTask = savedInstanceState.getBoolean("mResumeFetchWebPageTask");
			mResumeFetchBookInfoTask = savedInstanceState.getBoolean("mResumeFetchBookInfoTask");
			
			mPrice = savedInstanceState.getString("mPrice");
	        if (mPriceTextView != null) {
	        	mPriceTextView.setText(getResources().getString(R.string.text_price_text, mPrice));
	        }
	        
			mTitle = savedInstanceState.getString("mTitle");
	        if (mTitleTextView != null) {
	        	mTitleTextView.setText(mTitle);
	        }	        
		}
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(TAG, "onSaveInstanceState");
		if (mFetchBookInfoTask != null) {
			outState.putBoolean("mResumeFetchBookInfoTask", true);
		} else if (mFetchWebPageTask != null) {
			outState.putBoolean("mResumeFetchWebPageTask", true);
		}
		outState.putString("mISBN", mISBN);
		outState.putInt("mStores", mStores);
		outState.putInt("mCurrentStore", mCurrentStore);
		outState.putString("mPrice", mPrice);
		outState.putString("mTitle", mTitle);
		super.onSaveInstanceState(outState);
	}

	private class SavedObject {
		public BooksAdapter mAdapter = null;
		public Bitmap mCover = null;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		SavedObject savedObject = new SavedObject();
		savedObject.mAdapter = mAdapter;
		savedObject.mCover = mCover;
		
		return savedObject;
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
        	SavedObject savedObject = (SavedObject) getLastNonConfigurationInstance();
        	mAdapter = savedObject.mAdapter;
        	mCover = savedObject.mCover;
        }
        
        setContentView(R.layout.bookinfo);
        
        Intent intent = getIntent();
        if (intent != null) {
        	Bundle bundle = intent.getExtras();
        	if (bundle != null) {
            	mISBN = bundle.getString("ISBN");
        	}
        }
        
        if (mCoverImageView == null) {
        	mCoverImageView = (ImageView) findViewById(R.id.image_cover);
        }
        if (mCover != null) {
        	mCoverImageView.setImageBitmap(mCover);
        }
        
        if (mTitleTextView == null) {
        	mTitleTextView = (TextView) findViewById(R.id.text_title);
        }

        if (mPriceTextView == null) {
        	mPriceTextView = (TextView) findViewById(R.id.text_price);
        }

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
				
				String url = book.url;
				if (url == null) {
					url = getBookURLbyISBN(isbn, book.bookstore);
				}

    			Intent i = new Intent(Intent.ACTION_VIEW);
    			i.setData(Uri.parse(url));
    			startActivity(i);
			}
        });        

        SharedPreferences settings = getSharedPreferences("Findbooks", 0);
        boolean bFetchBookInfo = settings.getBoolean("pref_load_info", true);
        if (settings.getBoolean("pref_enable_findbook", true)) {
        	mStores |= 1 << BOOKSTORE_FINDBOOK - BOOKSTORE_START;
        }
        if (settings.getBoolean("pref_enable_eslite", true)) {
        	mStores |= 1 << BOOKSTORE_ESLITE - BOOKSTORE_START;
        }
        if (settings.getBoolean("pref_enable_books", true)) {
        	mStores |= 1 << BOOKSTORE_BOOKS - BOOKSTORE_START;
        }
        if (settings.getBoolean("pref_enable_sanmin", true)) {
        	mStores |= 1 << BOOKSTORE_SANMIN - BOOKSTORE_START;
        }
        if (settings.getBoolean("pref_enable_kingstone", true)) {
        	mStores |= 1 << BOOKSTORE_KINGSTONE - BOOKSTORE_START;
        }
        if (settings.getBoolean("pref_enable_tenlong", true)) {
        	mStores |= 1 << BOOKSTORE_TENLONG - BOOKSTORE_START;
        }

		LinearLayout info_container = (LinearLayout) findViewById (R.id.info_container);
		info_container.setVisibility(bFetchBookInfo ? View.VISIBLE : View.GONE);

        if (mStores == 0) {
        	
        }
        else if (savedInstanceState == null) {
        	if (bFetchBookInfo) {
            	goFetchBookInfoTask(mISBN);
        	} else {
        		goFetchWebPageTask(mISBN);
        	}
        }
	}

    private String getBookURLbyISBN(String isbn, int nBookstore) {
    	if (isbn == null) {
    		return null;
    	}
    	
    	if (isbn.length() == 0) {
    		return null;
    	}

		String url_store = null;

		switch (nBookstore) {
		default:
		case BOOKSTORE_FINDBOOK:	url_store = String.format(URL_FINDBOOK, isbn); break;
		case BOOKSTORE_BOOKS:		url_store = String.format(URL_BOOKS, isbn); break;
		case BOOKSTORE_ESLITE:		url_store = String.format(URL_ESLITE, isbn); break;
		case BOOKSTORE_SANMIN:		url_store = String.format(URL_SANMIN, isbn); break;
		case BOOKSTORE_KINGSTONE:	url_store = String.format(URL_KINGSTONE, isbn); break;
		case BOOKSTORE_TENLONG:		url_store = String.format(URL_TENLONG, isbn); break;
		}

		return url_store;
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
        		holder.store = (TextView) convertView.findViewById(R.id.text_store);
        		holder.price = (TextView) convertView.findViewById(R.id.text_price);
        		
        		convertView.setTag(holder);
        	} else {
        		holder = (ViewHolder) convertView.getTag();
        	}

        	SearchItems book = mBooks.get(position);
        	holder.store.setText(mBookStores[book.bookstore - 1]);
        	if (book.price != null) {
        		holder.price.setVisibility(View.VISIBLE);
        		holder.price.setText(getResources().getString(R.string.text_price_text, book.price));
        	} else {
        		holder.price.setVisibility(View.GONE);
        		holder.store.setText(mBookStores[book.bookstore - 1] + " - " + book.name);
        	}

            return convertView;
        }

        public void clearBooks() {
        	mBooks.clear();
            notifyDataSetChanged();
        }

        public void addBooks(String isbn, int bookstore, String name, String url, String price) {
        	SearchItems input = new SearchItems();
        	input.isbn = isbn;
        	input.bookstore = bookstore;
        	input.name = name;
        	input.url = url;
        	input.price = price;
        	
            mBooks.add(0, input);
            notifyDataSetChanged();
        }
    }    
}
