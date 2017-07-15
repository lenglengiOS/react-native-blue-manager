package it.innove;


import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import com.facebook.react.bridge.*;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopScanManager extends ScanManager {

    private Callback mCallback=null;
	public LollipopScanManager(ReactApplicationContext reactContext, Context con, BleManager bleManager) {
		super(reactContext,con, bleManager);
	}

	@Override
	public void stopScan(Callback callback) {
		// update scanSessionId to prevent stopping next scan by running timeout thread
		scanSessionId.incrementAndGet();

        if(mCallback!=null) {
            mCallback.invoke("sds");
            mCallback=null;
        }
		getBluetoothAdapter().getBluetoothLeScanner().stopScan(mScanCallback);
		callback.invoke();
	}

	@Override
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback) {

        if(reactContext==null){
            mCallback = callback;
        }
		ScanSettings settings = new ScanSettings.Builder().build();
		List<ScanFilter> filters = new ArrayList<>();

		if (serviceUUIDs != null && serviceUUIDs.size() > 0) {
			for(int i = 0; i < serviceUUIDs.size(); i++){
				ScanFilter.Builder builder = new ScanFilter.Builder();
				builder.setServiceUuid(new ParcelUuid(UUIDHelper.uuidFromString(serviceUUIDs.getString(i))));
				filters.add(builder.build());
				Log.d(bleManager.LOG_TAG, "Filter service: " + serviceUUIDs.getString(i));
			}
		}

		getBluetoothAdapter().getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
		if (scanSeconds > 0) {
			Thread thread = new Thread() {
				private int currentScanSession = scanSessionId.incrementAndGet();

				@Override
				public void run() {

					try {
						Thread.sleep(scanSeconds * 1000);
					} catch (InterruptedException ignored) {
					}

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							BluetoothAdapter btAdapter = getBluetoothAdapter();
							// check current scan session was not stopped
							if (scanSessionId.intValue() == currentScanSession) {
								if(btAdapter.getState() == BluetoothAdapter.STATE_ON) {
									btAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
								}
								WritableMap map = Arguments.createMap();
								bleManager.sendEvent("BleManagerStopScan", map);
							}
                            if(mCallback!=null) {
                                mCallback.invoke();
                                mCallback=null;
                            }
						}
					});

				}

			};
			thread.start();
		}
        if(reactContext!=null) {
            callback.invoke();
        }
	}

	private ScanCallback mScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(final int callbackType, final ScanResult result) {

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Log.i(bleManager.LOG_TAG, "DiscoverPeripheral: " + result.getDevice().getName());
					String address = result.getDevice().getAddress();

					synchronized(bleManager.peripherals) {
						if (!bleManager.peripherals.containsKey(address)) {

							Peripheral peripheral = new Peripheral(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes(), reactContext);
							bleManager.peripherals.put(address, peripheral);
                            if(mCallback!=null) {
                                mCallback.invoke(result.getDevice().getName()+"<<>>"+address);
                            }
							try {
								Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
								WritableMap map = Arguments.fromBundle(bundle);
								bleManager.sendEvent("BleManagerDiscoverPeripheral", map);
							} catch (JSONException ignored) {

							}

						} else {
							// this isn't necessary
							Peripheral peripheral = bleManager.peripherals.get(address);
							peripheral.updateRssi(result.getRssi());
                            if(mCallback!=null) {
                                mCallback.invoke(result.getDevice().getName()+"<<>>"+address);
                            }
						}
					}
				}
			});
		}

		@Override
		public void onBatchScanResults(final List<ScanResult> results) {
		}

		@Override
		public void onScanFailed(final int errorCode) {
		}
	};
}
