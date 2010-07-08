package com.corner23.android.findbooks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.net.Proxy;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class BitmapCache {
	
	private static BitmapCache instance = null;

	private ConcurrentHashMap<URL, SoftReference<Bitmap>> cache = new ConcurrentHashMap<URL, SoftReference<Bitmap>>();
    private static final int DEFAULT_READ_TIMEOUT = 10000;
    private static final int DEFAULT_CONN_TIMEOUT = 10000;
    private static final int IO_BUFFER_SIZE = 4 * 1024;
    private static final int MAX_PICTURE_SIZE = 70000;	// 70K
    
    private Proxy httpProxy = null;
    private String httpProxyHost = null;
    private Integer httpProxyPort;
    
	public BitmapCache(boolean bHasProxy) {
		if (bHasProxy) {
			this.configureHttpProxy();
		}
	}

	public static BitmapCache getInstance(boolean bHasProxy) {
		if (instance == null) {
			instance = new BitmapCache(bHasProxy);
		}
		return instance;
	}

	public boolean containsURL(URL url) {
		if (url == null)
			return false;

		return cache.containsKey(url);
	}
	
	private Bitmap get(URL url) {
		if (url == null)
			return null;

		Bitmap bitmap = null;
		SoftReference<Bitmap> reference = cache.get(url);
		if (reference != null) {
			bitmap = reference.get();
		}
		
		return bitmap;
	}
		
	public Bitmap load(URL url) {
		if (url == null)
			return null;

		if (containsURL(url)) { 
			Bitmap bitmap = get(url); 
			if (bitmap != null) {
				return bitmap;
			}
		}
		
		return fetch(url);
	}
	
    private void configureHttpProxy() {
        this.httpProxyHost = android.net.Proxy.getDefaultHost();
        this.httpProxyPort = android.net.Proxy.getDefaultPort();
        if (this.httpProxyHost != null && this.httpProxyPort != null) {
        	SocketAddress proxyAddress = new InetSocketAddress( this.httpProxyHost, this.httpProxyPort);
        	this.httpProxy = new Proxy(Proxy.Type.HTTP, proxyAddress);
        } else {
        	this.httpProxy = Proxy.NO_PROXY;
        }
    }
    
    /**
     * Copy the content of the input stream into the output stream, using a temporary
     * byte array buffer whose size is defined by {@link #IO_BUFFER_SIZE}.
     *
     * @param in The input stream to copy from.
     * @param out The output stream to copy to.
     *
     * @throws IOException If any error occurs during the copy.
     */
    private static int copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[IO_BUFFER_SIZE];
        int read, size = 0;
        
	    while ((read = in.read(b)) != -1) {
	        out.write(b, 0, read);
	        size += read;
	    }
        return size;
    }

    /**
     * Closes the specified stream.
     *
     * @param stream The stream to close.
     */
    private static void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
            	e.printStackTrace();
            }
        }
    }

    private synchronized Bitmap fetch(URL url) {
		if (url == null)
			return null;
		
		// Double confirm if last thread has fetched the bitmap.
		if (containsURL(url)) { 
			Bitmap bitmap = get(url); 
			if (bitmap != null) {
				return bitmap;
			}
		}

		Bitmap bitmap = null;
        InputStream in = null;
        OutputStream out = null;
        URLConnection urlc = null;
        
		try {
			// If proxy has been set, check if it changed
			if (this.httpProxyHost != null) {
				String proxy = android.net.Proxy.getDefaultHost();
				if (proxy != null && !proxy.equals(this.httpProxyHost) || 
					android.net.Proxy.getDefaultPort() != this.httpProxyPort) {
					this.configureHttpProxy();
				}
			}
			
			if (this.httpProxy != null) {
				urlc = url.openConnection(this.httpProxy);
			} else {
				urlc = url.openConnection();
			}
			urlc.setRequestProperty("User-Agent", "Mozilla/5.0");
			urlc.setReadTimeout(DEFAULT_READ_TIMEOUT);
			urlc.setConnectTimeout(DEFAULT_CONN_TIMEOUT);
			String url_orig = urlc.getURL().toString();
			in = new BufferedInputStream(urlc.getInputStream(), IO_BUFFER_SIZE);
			String url_after = urlc.getURL().toString();
			if (!url_after.equals(url_orig)) {
				Log.d("BitmapCache", "redirected..");
				return null;
			}

            ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
            out = new BufferedOutputStream(dataStream, IO_BUFFER_SIZE);
            int size = copy(in, out);
            out.flush();

		    BitmapFactory.Options options = new BitmapFactory.Options();
		    if (size > MAX_PICTURE_SIZE) {
				// set sample size to 5 avoiding memory exception
				options.inSampleSize = 5;
		    }

		    byte[] data = dataStream.toByteArray();
		    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
				
			cache.put(url, new SoftReference<Bitmap>(bitmap));
		} catch (UnknownHostException e) {
			// e.printStackTrace();
			closeStream(in);
			closeStream(out);
			return null;
		} catch (IOException e) {
			// e.printStackTrace();
			closeStream(in);
			closeStream(out);
			return null;
		} catch (Exception e) {
			// e.printStackTrace();
			closeStream(in);
			closeStream(out);
			return null;
		} finally {
			closeStream(in);
			closeStream(out);
		}
		
		return bitmap;			
	}
}