/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.backends.gwt.preloader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import com.badlogic.gdx.backends.gwt.preloader.AssetFilter.AssetType;
import com.badlogic.gdx.backends.gwt.preloader.BinaryLoader.Blob;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.ImageElement;

public class Preloader {
	public interface PreloaderCallback {
		public void done ();

		public void loaded (String file, int loaded, int total);

		public void error (String file);
	}

	public ObjectMap<String, ImageElement> images = new ObjectMap<String, ImageElement>();
	public ObjectMap<String, Void> audio = new ObjectMap<String, Void>();
	public ObjectMap<String, String> texts = new ObjectMap<String, String>();
	public ObjectMap<String, Blob> binaries = new ObjectMap<String, Blob>();

	private class Asset {
		String url;
		AssetType type;

		public Asset (String url, AssetType type) {
			this.url = url;
			this.type = type;
		}
	}

	public final String baseUrl;

	public Preloader () {
		baseUrl = GWT.getModuleBaseURL().replace(GWT.getModuleName() + "/", "") + "assets/";
		// trigger copying of assets creation of assets.txt
		GWT.create(PreloaderBundle.class);
	}

	public Array<Asset> assets;
	public int loaded = 0;
	
	public void preload (final String assetFileUrl, final PreloaderCallback callback) {
		new TextLoader(baseUrl + assetFileUrl, new LoaderCallback<String>() {
			@Override
			public void success (String result) {
				String[] lines = result.split("\n");
				assets = new Array<Asset>();
				for (String line : lines) {
					String[] tokens = line.split(":");
					if (tokens.length != 2) continue; // FIXME :p
					AssetType type = AssetType.Text;
					if (tokens[0].equals("i")) type = AssetType.Image;
					if (tokens[0].equals("b")) type = AssetType.Binary;
					if (tokens[0].equals("a")) type = AssetType.Audio;
					if (tokens[0].equals("d")) type = AssetType.Directory;
					assets.add(new Asset(tokens[1].trim(), type));
				}
				for (Asset asset : assets) {
					beginLoadingAsset(asset, callback);
				}
			}

			@Override
			public void error () {
				callback.error(assetFileUrl);
			}
		});
	}

	public void beginLoadingAsset (final Asset asset, final PreloaderCallback callback) {
		if (asset.type == AssetType.Text) {
			new TextLoader(baseUrl + asset.url, new LoaderCallback<String>() {
				@Override
				public void success (String result) {
					texts.put(asset.url, result);					
					finishLoadingAsset(asset, callback, true);
				}

				@Override
				public void error () {
					finishLoadingAsset(asset, callback, false);
				}
			});
		}

		if (asset.type == AssetType.Image) {
			new ImageLoader(baseUrl + asset.url, new LoaderCallback<ImageElement>() {
				@Override
				public void success (ImageElement result) {
					images.put(asset.url, result);
					finishLoadingAsset(asset, callback, true);
				}

				@Override
				public void error () {
					finishLoadingAsset(asset, callback, false);
				}
			});
		}

		if (asset.type == AssetType.Binary) {
			new BinaryLoader(baseUrl + asset.url, new LoaderCallback<Blob>() {
				@Override
				public void success (Blob result) {
					binaries.put(asset.url, result);
					finishLoadingAsset(asset, callback, true);
				}

				@Override
				public void error () {
					finishLoadingAsset(asset, callback, false);
				}
			});
		}

		if (asset.type == AssetType.Audio) {
			new AudioLoader(baseUrl + asset.url, new LoaderCallback<Void>() {
				@Override
				public void success (Void result) {
					audio.put(asset.url, null);
					finishLoadingAsset(asset, callback, true);
				}

				@Override
				public void error () {
					finishLoadingAsset(asset, callback, false);
				}
			});
		}

		if (asset.type == AssetType.Directory) {
			finishLoadingAsset(asset, callback, true);
		}
	}
	
	public void finishLoadingAsset (final Asset asset, final PreloaderCallback callback, boolean success) {
		loaded++;
		if (success) {
			callback.loaded(asset.url, loaded, assets.size);
		} else {
			callback.error(asset.url);
		}
		if (loaded == assets.size) {
			callback.done();
		}
	}

	public InputStream read (String url) {
		if (texts.containsKey(url)) {
			try {
				return new ByteArrayInputStream(texts.get(url).getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
				return null;
			}
		}
		if (images.containsKey(url)) {
			return new ByteArrayInputStream(new byte[1]); // FIXME, sensible?
		}
		if (binaries.containsKey(url)) {
			return binaries.get(url).read();
		}
		if (audio.containsKey(url)) {
			return new ByteArrayInputStream(new byte[1]); // FIXME, sensible?
		}
		return null;
	}

	public boolean contains (String url) {
		// FIXME should also check if directory exists
		return texts.containsKey(url) || images.containsKey(url) || binaries.containsKey(url) || audio.containsKey(url);
	}

	public boolean isText (String url) {
		return texts.containsKey(url);
	}

	public boolean isImage (String url) {
		return images.containsKey(url);
	}

	public boolean isBinary (String url) {
		return binaries.containsKey(url);
	}

	public boolean isAudio (String url) {
		return audio.containsKey(url);
	}

	public FileHandle[] list (String url) {
		throw new GdxRuntimeException("Not implemented"); // FIXME
	}

	public boolean isDirectory (String url) {
		throw new GdxRuntimeException("Not implemented"); // FIXME
	}

	public long length (String url) {
		if (texts.containsKey(url)) {
			try {
				return texts.get(url).getBytes("UTF-8").length;
			} catch (UnsupportedEncodingException e) {
				return texts.get(url).getBytes().length;
			}
		}
		if (images.containsKey(url)) {
			return 1; // FIXME, sensible?
		}
		if (binaries.containsKey(url)) {
			return binaries.get(url).length();
		}
		if (audio.containsKey(url)) {
			return 1; // FIXME sensible?
		}
		return 0;
	}
}
