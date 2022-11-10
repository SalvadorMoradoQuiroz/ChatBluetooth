package com.polarinsdustries.chatbluetooth

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.NullPointerException
import java.nio.charset.Charset
import java.util.*

class BluetoothConnectionService(var mContext: Context) {

    private val mBluetoothAdapter: BluetoothAdapter
    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mmDevice: BluetoothDevice? = null
    private var deviceUUID: UUID? = null
    var mProgressDialog: ProgressDialog? = null
    private var mConnectedThread: ConnectedThread? = null

    //Servidor Bluetooth
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        // Socket servidor local bluetooth
        private val mmServerSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null
            // Crear una nueva conexión
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    appName,
                    MY_UUID_INSECURE
                )
            } catch (e: IOException) {
                Log.e("ERROR", "${e.message}")
            }
            mmServerSocket = tmp
        }

        override fun run() {
            var socket: BluetoothSocket? = null
            try {
                socket = mmServerSocket!!.accept()
            } catch (e: IOException) {
                Log.e("ERROR", "Exception Connection Server" + e.message)
            }
            socket?.let { connected(it, mmDevice) }
        }

        fun cancel() {
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e("Cancelar", "${e.message}")
            }
        }

    }

    // Cliente Bluetooth
    private inner class ConnectThread(device: BluetoothDevice?, uuid: UUID?) : Thread() {
        private var mmSocket: BluetoothSocket? = null

        init {
            mmDevice = device
            deviceUUID = uuid
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            var tmp: BluetoothSocket? = null
            try {
                tmp = mmDevice!!.createRfcommSocketToServiceRecord(deviceUUID)
            } catch (e: IOException) {
                Log.e("ERROR", "Exception Connection Client" + e.message)
            }
            mmSocket = tmp

            // Cancelar discovery, pone lenta la conexión
            mBluetoothAdapter.cancelDiscovery()

            // Conectarse con el socket de bluetooth
            try {
                mmSocket!!.connect()
                Log.d("SUCCESS", "Ya se conecto")
            } catch (e: IOException) {
                try {
                    mmSocket!!.close()
                } catch (e1: IOException) {
                    Log.e(
                        "ERROR", "No se pudo cerrar la conexion" + e1.message
                    )
                }
                Log.d("ERROR", "No se puede cerrar con el UUID " + MY_UUID_INSECURE)
            }
            connected(mmSocket, mmDevice)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e("ERROR", "FALLO LA CONCELACIÓN " + e.message)
            }
        }
    }

    // Inicio de la transferencia de datos
    @Synchronized
    fun start() {
        Log.d("INICIO", "Inicio de conexión")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread()
            mInsecureAcceptThread!!.start()
        }
    }

    //Intenta conectase con el dispsotivo bluetooth
    fun startClient(device: BluetoothDevice?, uuid: UUID?) {
        //Inicia la conexión
        mProgressDialog = ProgressDialog.show(
            mContext, "Conectando al bluetooth", "Espere...", true
        )
        mConnectThread = ConnectThread(device, uuid)
        mConnectThread!!.start()
    }

    //Clase que se encarga de mantener la conexión, enviar y recibir datos
    private inner class ConnectedThread(socket: BluetoothSocket?) : Thread() {

        private val mmSocket: BluetoothSocket?
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                mProgressDialog!!.dismiss()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024) // buffer store para transmitir
            var bytes: Int

            while (true) {
                // Hace la lectura desde le inpurStream
                try {
                    bytes = mmInStream!!.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)
                    Log.d(
                        "Recibido",
                        "InputStream: $incomingMessage"
                    )

                    var incomingMessageIntent: Intent = Intent("incomingMessage")
                    incomingMessageIntent.putExtra("Message", incomingMessage)
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(incomingMessageIntent)
                } catch (e: IOException) {
                    Log.e(TAG, "Error al leer el mensaje" + e.message)
                    break
                }
            }
        }

        //Metodo para enviar texto
        fun write(bytes: ByteArray?) {
            val text = String(bytes!!, Charset.defaultCharset())
            Log.e(
                "Escribiendo",
                "Salida: $text"
            )
            try {
                mmOutStream!!.write(bytes)
            } catch (e: IOException) {
                Log.e("ERROR", "Error al escribir el mensaje " + e.message)
            }
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
            }
        }

    }

    private fun connected(mmSocket: BluetoothSocket?, mmDevice: BluetoothDevice?) {
        mConnectedThread = ConnectedThread(mmSocket)
        mConnectedThread!!.start()
    }

    fun write(out: ByteArray?) {
        // Crear un objeto temporal
        var r: ConnectedThread
        //Escribiendo
        mConnectedThread!!.write(out)
    }

    companion object {
        private const val TAG = "BluetoothConnectionServ"
        private const val appName = "MYAPP"
        private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    init {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        start()
    }
}
