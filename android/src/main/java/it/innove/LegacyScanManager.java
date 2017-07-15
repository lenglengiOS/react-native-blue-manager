package it.innove;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import com.facebook.react.bridge.*;
import org.json.JSONException;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

public class LegacyScanManager extends ScanManager {

    private Callback mCallback=null;
	public LegacyScanManager(ReactApplicationContext reactContext, Context con, BleManager bleManager) {
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
		getBluetoothAdapter().stopLeScan(mLeScanCallback);
		callback.invoke();
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {


				@Override
				public void onLeScan(final BluetoothDevice device, final int rssi,
									 final byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Log.i(bleManager.LOG_TAG, "DiscoverPeripheral: " + device.getName());
							String address = device.getAddress();
							Log.i(bleManager.LOG_TAG, "address: " + address);

							synchronized(bleManager.peripherals) {
								if (!bleManager.peripherals.containsKey(address)) {

									Peripheral peripheral = new Peripheral(device, rssi, scanRecord, reactContext);
									bleManager.peripherals.put(address, peripheral);
									Log.e(bleManager.LOG_TAG, "peripherals: put ");
                                    if(mCallback!=null) {
                                        mCallback.invoke(device.getName()+"<<>>"+address);
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
									peripheral.updateRssi(rssi);
									Log.e(bleManager.LOG_TAG, "peripherals: update ");
                                    if(mCallback!=null) {
                                        mCallback.invoke(device.getName()+"<<>>"+address);
                                    }
								}
							}
						}
					});
				}


			};

	@Override
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback) {
		Log.i(bleManager.LOG_TAG, "scan: " + scanSeconds);
        if(reactContext==null){
            mCallback = callback;
        }
		getBluetoothAdapter().startLeScan(mLeScanCallback);

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
								if (btAdapter.getState() == BluetoothAdapter.STATE_ON) {
									btAdapter.stopLeScan(mLeScanCallback);
                                    Log.e(bleManager.LOG_TAG, "peripherals: stop scan ");
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
}
