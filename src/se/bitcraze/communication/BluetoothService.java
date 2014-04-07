package se.bitcraze.communication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;



public class BluetoothService extends Service {
	
	// Binder given to clients
    private final IBinder mBinder = new BlueoothBinder();
	
	private static final String TAG = "BTService";
	
	private BluetoothAdapter mBluetoothAdapter = null;
	private ConnectedThread mConnectedThread;
	
	private LinkedHashSet<String> bluetoothDevicesName;
	
	private BluetoothInterface bluetoothInterface;
	private Set<BluetoothDevice> bluetoothDevices;
	private BluetoothSocket mBluetoothSocket = null;
	private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	public class BlueoothBinder extends Binder {
		public BluetoothService getService() {
            return BluetoothService.this;
        }
    }
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Log.v(TAG, "onCreate BTService");
		
		bluetoothDevices = new HashSet<BluetoothDevice>();
		bluetoothDevicesName = new LinkedHashSet<String>();
		
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		return Service.START_STICKY;
	}
	

	
	
	public void  setBluetoothInterface(BluetoothInterface bluetoothInter) {
		this.bluetoothInterface = bluetoothInter;
	}
	
	
	public void startBluetoothDiscovery() {
		if(checkBluetooth()){
			mBluetoothAdapter.startDiscovery();
		}
	}
	
	public void cancelBluetoothDiscovery() {
		mBluetoothAdapter.cancelDiscovery();
	}
	
	public void connectBluetoothDevice() {
		cancelBluetoothDiscovery();
		
		// Cancel any thread currently running a connection
        if (mConnectedThread != null) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }
		
		Object[] deviceses = bluetoothDevices.toArray();
		BluetoothDevice mBluetoothDevice = (BluetoothDevice) deviceses[0];
		
		try {
			mBluetoothSocket = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
			mBluetoothSocket.connect();
			// Start the thread to manage the connection and perform transmissions
	        mConnectedThread = new ConnectedThread(mBluetoothSocket, "Insecure");
	        mConnectedThread.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 判斷藍芽裝置是否正常及開啟
	 * @return 藍芽裝置是否正常及開啟 (true = 沒問題)
	 */
	private boolean checkBluetooth() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		// 裝置不支援藍芽
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "本设备不支持蓝牙", Toast.LENGTH_SHORT).show();
			return false;
		}

		// 藍芽沒有開啟
		if (!mBluetoothAdapter.isEnabled()) {
			Toast.makeText(this, "蓝牙没有开启", Toast.LENGTH_SHORT).show();
			return false;
		}

		return true;
	}
	
	
	// Create a BroadcastReceiver for ACTION_FOUND
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	            // Add the name and address to an array
	            
	            bluetoothDevicesName.add(device.getName() + "\n" + device.getAddress());
	            bluetoothDevices.add(device);
	            
	            if (null != bluetoothInterface) {
					bluetoothInterface.bluetoothDevicesUpdate(bluetoothDevicesName);
				}
	            
	            Log.v(TAG, device.getName() + "\n" + device.getAddress());
	        }
	    }
	};    
	
	
	/**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    
                    Log.v(TAG,new String(buffer, 0, bytes - 1));
                    
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
	
	
	
	

}