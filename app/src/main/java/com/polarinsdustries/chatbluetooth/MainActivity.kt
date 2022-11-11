package com.polarinsdustries.chatbluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.nio.charset.Charset
import java.util.*

class MainActivity : AppCompatActivity() , AdapterView.OnItemSelectedListener {

    companion object {
        private const val TAG = "MainActivity"
        private val MY_UUID_INSECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    //BT
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBTDevices: ArrayList<BluetoothDevice>? = null
    private var mDeviceListAdapter: DeviceListAdapter? = null
    private var lvNewDevices: ListView? = null
    private var btDevice:BluetoothDevice? = null

    //BT libBluetooth
    private var deviceMAC: String? = null
    private var deviceName: String? = null

    private var mBluetoothConnection: BluetoothConnectionService? = null

    private var flagBluetooth: Boolean = false
    private var switch_BtActivate: SwitchMaterial? = null
    private var textView_DeviceSelected: TextView? = null
    private var button_Send:Button? = null
    private var textView_Messages:TextView? = null
    private var editText_Message:EditText? = null

    private var messages:StringBuilder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button_Send = findViewById(R.id.button_Send)
        textView_Messages = findViewById(R.id.textView_Messages)
        editText_Message = findViewById(R.id.editText_Message)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()


        messages = StringBuilder()
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, IntentFilter("incomingMessage"))

        button_Send!!.setOnClickListener{
            if(btDevice!=null){
                val bytes: ByteArray = editText_Message?.text.toString().toByteArray(Charset.defaultCharset())
                mBluetoothConnection?.write(bytes)
                editText_Message?.setText("")
            }else{
                Toast.makeText(this@MainActivity, "Se debe conectar a un dispositivo BT", Toast.LENGTH_SHORT).show()
            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_opciones, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        when (item.getItemId()) {
            R.id.conf_bt -> showDialogConfBt()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        TODO("Not yet implemented")
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }

    //BLUETOOTH-------------------------------------------------------------------------------------
    //Método  para recibir de bt
    @SuppressLint("MissingPermission")
    fun showDialogConfBt() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.layout_config_bt, null)
        builder.setView(view)
        val dialogConfBt: AlertDialog = builder.create()
        dialogConfBt.setCancelable(false)
        dialogConfBt.show()

        this.switch_BtActivate =
            dialogConfBt.findViewById(R.id.switch_BtActivated) as SwitchMaterial
        var button_VisibleBt = dialogConfBt.findViewById(R.id.button_VisibleBt) as Button
        var button_SearchDevicesBt =
            dialogConfBt.findViewById(R.id.button_SearchDevicesBt) as Button
        lvNewDevices = dialogConfBt.findViewById(R.id.listView_DevicesBt) as ListView
        textView_DeviceSelected =
            dialogConfBt.findViewById(R.id.textView_DeviceSelected) as TextView
        var button_ConnectBt = dialogConfBt.findViewById(R.id.button_ConnectBt) as Button
        var button_CloseConfigBt = dialogConfBt.findViewById(R.id.button_CloseConfigBt) as Button

        mBTDevices = ArrayList<BluetoothDevice>()
        mDeviceListAdapter =
            DeviceListAdapter(applicationContext, R.layout.device_adapter_view, mBTDevices!!)
        lvNewDevices?.adapter = mDeviceListAdapter

        if (mBluetoothAdapter!!.isEnabled) {
            flagBluetooth = true
            this.switch_BtActivate!!.setText("Bluetooth activado")
            this.switch_BtActivate!!.isChecked = true
        }

        if (this.btDevice != null) {
            deviceMAC = btDevice!!.address
            textView_DeviceSelected!!.setText("Dispositivo seleccionado: " + deviceMAC + " esta conectado.")
        }

        this.switch_BtActivate!!.setOnClickListener {
            enableDisableBT()
        }

        button_VisibleBt.setOnClickListener { doVisibleBT() }

        button_SearchDevicesBt.setOnClickListener { searchBT() }

        lvNewDevices!!.setOnItemClickListener(
            AdapterView.OnItemClickListener { parent, view, position, id ->
                mBluetoothAdapter?.cancelDiscovery()
                this.btDevice = mBTDevices!!.get(position)
                this.deviceMAC = mBTDevices!!.get(position).getAddress()
                this.deviceName = mBTDevices!!.get(position).getName()
                textView_DeviceSelected!!.setText(
                    "Dispositivo seleccionado: " + mBTDevices!!.get(
                        position
                    ).toString()
                )
            }
        )

        button_ConnectBt.setOnClickListener {
            if(btDevice!=null){
                mBluetoothConnection = BluetoothConnectionService(this@MainActivity)
                startBTConnection(btDevice, MY_UUID_INSECURE)
            }
        }
        button_CloseConfigBt.setOnClickListener { dialogConfBt.dismiss() }
    }

    @SuppressLint("MissingPermission")
    private fun doVisibleBT() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverableIntent)
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        registerReceiver(mBroadcastReceiver2, intentFilter)
    }

    private val mBroadcastReceiver1: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state: Int =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        flagBluetooth = false
                        this@MainActivity.switch_BtActivate!!.setText("Bluetooth desactivado")
                        this@MainActivity.switch_BtActivate!!.isChecked = false
                        Toast.makeText(applicationContext, "Bluetooth apagado", Toast.LENGTH_SHORT)
                            .show()
                        if (mBTDevices != null) {
                            mBTDevices!!.clear()
                            mDeviceListAdapter!!.notifyDataSetChanged()
                            textView_DeviceSelected!!.setText("Dispositivo seleccionado: ")
                            deviceMAC = null
                            btDevice = null
                        }
                        mBTDevices!!.clear();
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Toast.makeText(applicationContext, "Apagando bluetooth", Toast.LENGTH_SHORT)
                            .show()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        this@MainActivity.switch_BtActivate!!.setText("Bluetooth activado")
                        this@MainActivity.switch_BtActivate!!.isChecked = true
                        flagBluetooth = true
                        Toast.makeText(
                            applicationContext,
                            "Bluetooth encendido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        Toast.makeText(
                            applicationContext,
                            "Encendiendo bluetooth",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 500) {//Intent para encender bluetooth o no
            if (resultCode == 0) {//Se rechazo
                this@MainActivity.switch_BtActivate!!.setText("Bluetooth desactivado")
                this@MainActivity.switch_BtActivate!!.isChecked = false
            }
        }
        Log.e("requestCode", requestCode.toString())
        Log.e("resultCode", resultCode.toString())
    }

    //Para ver los cambios de estado del bluetooth, si se enciende o expira discovery
    private val mBroadcastReceiver2: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val mode: Int =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                when (mode) {
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad habilitada.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad deshabilitada. Capaz de recibir conexiones.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.SCAN_MODE_NONE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad deshabilitada. No capaz de recibir conexiones.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.STATE_CONNECTING -> {
                        Toast.makeText(applicationContext, "Conectando...", Toast.LENGTH_SHORT)
                            .show()
                    }
                    BluetoothAdapter.STATE_CONNECTED -> {
                        Toast.makeText(applicationContext, "Conectado.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    //Para recibir la lista de dispositivos disponibles btnDiscover
    private val mBroadcastReceiver3: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            Log.d(TAG, "onReceive: ACTION FOUND.")
            Log.d("action", action!!)
            if (action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (!mBTDevices!!.contains(device!!)) {
                    Log.d(TAG, "onReceive: " + device?.getName().toString() + ": " + device.address)
                    mBTDevices!!.add(device!!)
                    mDeviceListAdapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun searchBT() {
        checkBTPermissions()

        var location: LocationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var flagBT: Boolean = mBluetoothAdapter!!.isEnabled
        var flagGPS: Boolean = location.isLocationEnabled

        if (flagBT && flagGPS) {

            mBTDevices!!.clear()
            mDeviceListAdapter!!.notifyDataSetChanged()

            if (mBluetoothAdapter!!.isDiscovering()) {
                mBluetoothAdapter!!.cancelDiscovery()
                checkBTPermissions()
                mBluetoothAdapter?.startDiscovery()
                val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
            } else if (!mBluetoothAdapter?.isDiscovering()!!) {
                checkBTPermissions()
                mBluetoothAdapter?.startDiscovery()
                val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
            }
        } else {
            if (!flagGPS) {
                Toast.makeText(applicationContext, "Debes encender la ubicación", Toast.LENGTH_LONG)
                    .show()
                locationOn(location)
            }
            if (flagGPS && (!flagBT)) {
                Toast.makeText(
                    applicationContext,
                    "Debes encender el bluetooth",
                    Toast.LENGTH_SHORT
                ).show()
                enableDisableBT()
            }
        }
    }

    private fun locationOn(location: LocationManager) {
        var intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivityForResult(intent, 501)
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkBTPermissions() {
        var permissionCheck: Int =
            this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")

        var permiso2: Int =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)

        if (permissionCheck != 0 && permiso2 != 0) {
            this.requestPermissions(
                arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1001
            ) //Any number
        }
    }

    @SuppressLint("MissingPermission")
    fun enableDisableBT() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(
                applicationContext,
                "El dispositivo no tiene bluetooth",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("ERROR:", "El dispositivo no tiene bluetooth")
        }
        if (!mBluetoothAdapter!!.isEnabled) {
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBTIntent, 500)
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
        if (mBluetoothAdapter!!.isEnabled) {
            mBluetoothAdapter!!.disable()
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
    }

    fun startBTConnection(device: BluetoothDevice?, uuid: UUID?) {
        mBluetoothConnection?.startClient(device, uuid)
    }

    //Recibir un mensaje y lo muestra en el textView
    var mReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent){
            var text: String? = intent.getStringExtra("Message")
            messages?.append("$text \n")
            textView_Messages?.setText(messages)
        }
    }

}