package keywallet.hid.device;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;

public class KeyWalletHidDevice implements KeyWalletDevice {

    ////////////////////////////////////////////////////////////////////////
    //// Type Definitions //////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    //// Constant /////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////

    private final byte REP_ONE_BLOCK = (byte) 0xA5;
    private final byte REP_FIRST_BLOCK = (byte) 0xA1;
    private final byte REP_MIDDLE_BLOCK = (byte) 0x11;
    private final byte REP_LAST_BLOCK = (byte) 0x15;

    private final byte TYPE_UNKNOWN = (byte) 0x00;
    private final byte TYPE_KEYWALLET_CMD = (byte) 0x01;
    private final byte TYPE_KEYWALLET_RSP = (byte) 0x02;
    private final byte TYPE_SMARTCARD_CMD = (byte) 0x03;
    private final byte TYPE_SMARTCARD_RSP = (byte) 0x04;
    private final byte TYPE_ACK = (byte) 0xFE;
    private final byte TYPE_NAK = (byte) 0xFF;

    private final byte SC_CONTROL = (byte) 0xFF;
    private final byte SC_INITIALIZE = (byte) 0x00;
    private final byte SC_IS_INITIALIZED = (byte) 0x01;
    private final byte SC_FINALIZE = (byte) 0x02;
    private final byte SC_IS_PRESENT = (byte) 0x03;
    private final byte SC_POWER_ON = (byte) 0x04;
    private final byte SC_IS_POWERED_ON = (byte) 0x05;
    private final byte SC_POWER_OFF = (byte) 0x06;
    private final byte SC_GET_ATR = (byte) 0x07;

    private final short SUCCESS = 0x0000;
    private final short FAIL = 0x0001;

    private final short FAIL_LIB_INPUT = 0x0101;
    private final short FAIL_LIB_NOT_INITIALIZED = 0x0102;
    private final short FAIL_LIB_ALREADY_INITIALIZED = 0x0103;

    private final short FAIL_KSC_NOT_POWERED_ON = 0x0201;
    private final short FAIL_KSC_ALREADY_POWERED_ON = 0x0202;
    private final short FAIL_KSC_NOT_POWERED_OFF = 0x0203;
    private final short FAIL_KSC_ALREADY_POWERED_OFF = 0x0204;

    private final short FAIL_KSC_ATR = 0x0301;
    private final short FAIL_KSC_PPS = 0x0302;
    private final short FAIL_KSC_TX = 0x0303;
    private final short FAIL_KSC_RX = 0x0304;
    private final short FAIL_KSC_CHAINING = 0x0305;
    private final short FAIL_KSC_APDU_FORMAT = 0x0306;

    private final short FAIL_UNKNOWN_CMD_TYPE = (short) 0xFF01;
    private final short FAIL_UNKNOWN_KEYWALLET_CMD = (byte) 0xFF02;
    private final short FAIL_UNKNOWN_SMARTCARD_CMD = (byte) 0xFF03;

    public final short KSC_UNKNOWN = (byte) 0xFFFF;
    public final short KSC_NOT_PRESENT = (byte) 0x0000;
    public final short KSC_PRESENT = (byte) 0x0001;
    public static String TAG = "MainActivity";
    private static final int TIMEOUT = 3500;
    //// Protocol //////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    //// Properties ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    // private UsbHidPort m_KeyWallet;
    private boolean m_fInReportEmpty;
    private byte[] m_abInReport = new byte[64];
    private byte[] m_abApduBuffer = new byte[3082];

    public static int END_POINT_SEND = 1;
    public static int END_POINT_RECEIVE = 0;

    PendingIntent mPIntent;
    public UsbDevice mDevice;
    UsbManager mUsbManager;
    UsbDeviceConnection mConnection;
    UsbInterface mInterface;
    UsbEndpoint mEndpoint;
    Context mContext;
    int mDevicePID;
    int mDeviceVID;
    String mDevicePName;
    boolean isPowerOn = false;
    private static final String ACTION_USB_PERMISSION = "com.example.doye.usbtest.USB_PERMISSION";

    KeyWalletHidDevice(UsbDevice usbDevice, UsbManager mUsbManager, Context context) {
        this.mDevice = usbDevice;
        this.mDevicePID = usbDevice.getProductId();
        this.mDeviceVID = usbDevice.getVendorId();
        this.mDevicePName = usbDevice.getProductName();
        this.mUsbManager = mUsbManager;
        this.mContext = context;
    }

    @Override
    public void Connection() {
        try {
            if (mUsbManager.hasPermission(mDevice)) {
                mInterface = mDevice.getInterface(0);
                mConnection = mUsbManager.openDevice(mDevice);
                mConnection.claimInterface(mInterface, true);
            } else {
                mPIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(mDevice, mPIntent);
            }

        } catch (NullPointerException e) {
            throw new NullPointerException();
        }

    }

    @Override
    public void DisConnect() {
        if (mConnection != null) {
            mConnection.releaseInterface(mInterface);
            mConnection.close();
        }
    }

    @Override
    public boolean IsCardPresent() throws IOException {
        if (mDevice == null) {
            String msg = "CardPresent [1] Error!!! (KeyWallet is not " + "selected.)";
            throw new NullPointerException(msg);
        }

        byte[] abOutReport = new byte[64];
        abOutReport[0] = REP_ONE_BLOCK;
        abOutReport[1] = TYPE_SMARTCARD_CMD;
        abOutReport[2] = 0x00;
        abOutReport[3] = 0x02;
        abOutReport[4] = SC_CONTROL;
        abOutReport[5] = SC_IS_PRESENT;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }
        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] != 0x02)) {
            String msg = "CardPresent [2] Error!!! (Invalid KeyWallet " + "Response)";
            throw new IOException(msg);
        }
        short result = (short) ((m_abInReport[4] << 8) + m_abInReport[5]);
        if (result == KSC_PRESENT) {
            return true;
        } else
            return false;
    }

    @Override
    public short PowerOn() throws IOException {

        if (mDevice == null) {
            String msg = "PowerOn [1] Error!!! (KeyWallet is not " + "selected.)";
            throw new NullPointerException(msg);
        }

        byte[] abOutReport = new byte[64];
        abOutReport[0] = REP_ONE_BLOCK;
        abOutReport[1] = TYPE_SMARTCARD_CMD;
        abOutReport[2] = 0x00;
        abOutReport[3] = 0x02;
        abOutReport[4] = SC_CONTROL;
        abOutReport[5] = SC_IS_INITIALIZED;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }

        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] != 0x02)) {
            String msg = "PowerOn [2] Error!!! (Initialize : Invalid " + "KeyWallet Response)";
            throw new IOException(msg);
        }

        if ((m_abInReport[4] == 0x00) && (m_abInReport[5] == 0x01)) {
            abOutReport[6] = SC_FINALIZE;

            // Send Report.
            synchronized (this) {
                m_fInReportEmpty = true;
                new DataTransferThread(abOutReport, END_POINT_SEND).start();
                new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
                while (m_fInReportEmpty)
                    ;
            }
        }

        abOutReport = new byte[64];
        abOutReport[0] = REP_ONE_BLOCK;
        abOutReport[1] = TYPE_SMARTCARD_CMD;
        abOutReport[2] = 0x00;
        abOutReport[3] = 0x02;
        abOutReport[4] = SC_CONTROL;
        abOutReport[5] = SC_INITIALIZE;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }

        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] != 0x02)) {
            String msg = "PowerOn [3] Error!!! (Initialize : Invalid " + "KeyWallet Response)";
            throw new IOException(msg);
        }

        short usRv = (short) ((m_abInReport[4] << 8) + m_abInReport[5]);
        if (usRv != SUCCESS) {
            String msg = "PowerOn [4] Error!!! (Initialize : " + ErrorString(usRv) + ")";
            throw new IOException(msg);
        }

        abOutReport[5] = SC_POWER_ON;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }

        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] != 0x02)) {
            String msg = "PowerOn [5] Error!!! (Invalid KeyWallet " + "Response)";
            throw new IOException(msg);
        }

        usRv = (short) ((m_abInReport[4] << 8) + m_abInReport[5]);
        if (usRv == SUCCESS) {
            isPowerOn = true;
            return usRv;
        } else {
            String msg = "PowerOn [6] Error!!! (" + ErrorString(usRv) + ")";
            throw new IOException(msg);
        }
    }

    @Override
    public short PowerOff() throws IOException {
        if (mDevice == null) {
            String msg = "PowerOff [1] Error!!! (KeyWallet is not " + "selected.)";
            throw new IOException(msg);
        }

        byte[] abOutReport = new byte[64];
        abOutReport[0] = REP_ONE_BLOCK;
        abOutReport[1] = TYPE_SMARTCARD_CMD;
        abOutReport[2] = 0x00;
        abOutReport[3] = 0x02;
        abOutReport[4] = SC_CONTROL;
        abOutReport[5] = SC_POWER_OFF;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }

        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] != 0x02)) {
            String msg = "PowerOff [2] Error!!! (Invalid KeyWallet " + "Response)";
            throw new IOException(msg);
        }

        short usRv = (short) ((m_abInReport[4] << 8) + m_abInReport[5]);
        if (usRv != SUCCESS) {
            String msg = "PowerOff [3] Error!!! (" + ErrorString(usRv) + ")";
            throw new IOException(msg);
        }

        abOutReport[5] = SC_FINALIZE;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }

        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] != 0x02)) {
            String msg = "PowerOff [4] Error!!! (Invalid KeyWallet " + "Response)";
            throw new IOException(msg);
        }

        usRv = (short) ((m_abInReport[4] << 8) + m_abInReport[5]);
        if (usRv == SUCCESS) {
            isPowerOn = false;
            return usRv;
        } else {
            String msg = "PowerOff [5] Error!!! (" + ErrorString(usRv) + ")";
            throw new IOException(msg);
        }
    }

    @Override
    public boolean IsPowerOn() {
        return isPowerOn;
    }

    @Override
    public byte[] GetAtr() throws IOException {
        if (mDevice == null) {
            String msg = "GetAtr [1] Error!!! (KeyWallet is not selected.)";
            throw new NullPointerException(msg);
        }

        byte[] abOutReport = new byte[64];
        abOutReport[0] = REP_ONE_BLOCK;
        abOutReport[1] = TYPE_SMARTCARD_CMD;
        abOutReport[2] = 0x00;
        abOutReport[3] = 0x02;
        abOutReport[4] = SC_CONTROL;
        abOutReport[5] = SC_GET_ATR;

        // Send Report.
        synchronized (this) {
            m_fInReportEmpty = true;
            new DataTransferThread(abOutReport, END_POINT_SEND).start();
            new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
            while (m_fInReportEmpty)
                ;
        }

        if ((m_abInReport[0] != REP_ONE_BLOCK) || (m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00)
                || (m_abInReport[3] < 0x02)) {
            String msg = "GetAtr [2] Error!!! (Invalid KeyWallet Response)";
            throw new IOException(msg);
        }

        short usRv = (short) ((m_abInReport[4] << 8) + m_abInReport[5]);
        if (usRv == SUCCESS) {
            short usSize = (short) ((m_abInReport[6] << 8) + m_abInReport[7]);
            byte[] abAtr = new byte[usSize];
            System.arraycopy(m_abInReport, 8, abAtr, 0, usSize);
            return abAtr;
        } else {
            String msg = "GetAtr [3] Error!!! (" + ErrorString(usRv) + ")";
            throw new IOException(msg);
        }
    }

    @Override
    public byte[] Transceive(byte[] abApduCmd) throws IOException {
        if (mDevice == null) {
            String msg = "Transceive [1] Error!!! (KeyWallet is not " + "selected.)";
            throw new NullPointerException(msg);
        }

        // Command APDU.
        byte[] abOutReport = new byte[64];
        byte bApduOffset = 0;
        short usApduSize = (short) abApduCmd.length;
        short usSize;
        while (usApduSize > 0) {
            if ((abOutReport[0] == 0x00) && (usApduSize <= 60))
                abOutReport[0] = REP_ONE_BLOCK;
            else if ((abOutReport[0] == 0x00) && (usApduSize > 60))
                abOutReport[0] = REP_FIRST_BLOCK;
            else if ((abOutReport[0] != 0x00) && (usApduSize > 60))
                abOutReport[0] = REP_MIDDLE_BLOCK;
            else
                abOutReport[0] = REP_LAST_BLOCK;

            usSize = (usApduSize > 60) ? (short) 60 : usApduSize;
            abOutReport[1] = TYPE_SMARTCARD_CMD;
            abOutReport[2] = bApduOffset;
            abOutReport[3] = (byte) usSize;
            System.arraycopy(abApduCmd, bApduOffset * 60, abOutReport, 4, usSize);
            bApduOffset++;
            usApduSize -= usSize;

            // Send Report.
            synchronized (this) {
                m_fInReportEmpty = true;
                new DataTransferThread(abOutReport, END_POINT_SEND).start();
                new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
                while (m_fInReportEmpty)
                    ;
            }

            if (((abOutReport[0] == REP_ONE_BLOCK) || (abOutReport[0] == REP_LAST_BLOCK))
                    && ((m_abInReport[1] != TYPE_SMARTCARD_RSP) || (m_abInReport[2] != 0x00))) {
                StringBuilder sb = new StringBuilder();
                for (byte b : m_abInReport) {
                    sb.append(String.format("%02X", b & 0xff));
                }
                String msg = "Transceive [2] Error!!! (Invalid KeyWallet " + "Response)";
                throw new IOException(msg + sb);
            } else if (((abOutReport[0] == REP_FIRST_BLOCK) || (abOutReport[0] == REP_MIDDLE_BLOCK))
                    && ((m_abInReport[0] != abOutReport[0]) || (m_abInReport[1] != TYPE_ACK)
                            || (m_abInReport[2] != abOutReport[2]) || (m_abInReport[3] != abOutReport[3]))) {
                String msg = "Transceive [3] Error!!! (Invalid KeyWallet " + "Response)";
                StringBuilder sb = new StringBuilder();
                for (byte b : m_abInReport) {
                    sb.append(String.format("%02X", b & 0xff));
                }
                throw new IOException(msg + sb);
            }
        }

        // Response APDU.
        bApduOffset = 0;
        usApduSize = m_abInReport[3];
        System.arraycopy(m_abInReport, 4, m_abApduBuffer, bApduOffset * 60, usApduSize);

        if (m_abInReport[0] == REP_FIRST_BLOCK) {
            do {
                System.arraycopy(m_abInReport, 0, abOutReport, 0, 4);
                abOutReport[1] = TYPE_ACK;

                // Send Report.
                synchronized (this) {
                    m_fInReportEmpty = true;
                    new DataTransferThread(abOutReport, END_POINT_SEND).start();
                    new DataTransferThread(m_abInReport, END_POINT_RECEIVE).start();
                    while (m_fInReportEmpty)
                        ;
                }

                if (m_abInReport[1] != TYPE_SMARTCARD_RSP) {
                    String msg = "Transceive [4] Error!!! (Invalid " + "KeyWallet Response)";
                    throw new IOException(msg);
                }

                bApduOffset = m_abInReport[2];
                usSize = m_abInReport[3];
                System.arraycopy(m_abInReport, 4, m_abApduBuffer, bApduOffset * 60, usSize);
                usApduSize += usSize;
            } while (m_abInReport[0] == REP_MIDDLE_BLOCK);
        }

        byte[] abApduRsp = new byte[usApduSize];
        System.arraycopy(m_abApduBuffer, 0, abApduRsp, 0, usApduSize);

        return abApduRsp;
    }

    public String ErrorString(short usErrorCode) {
        switch (usErrorCode) {
        case SUCCESS:
            return "Success.";
        case FAIL:
            return "Fail.";
        case FAIL_LIB_INPUT:
            return "KSC Library Input Error.";
        case FAIL_LIB_NOT_INITIALIZED:
            return "KSC Library Not Initialized.";
        case FAIL_LIB_ALREADY_INITIALIZED:
            return "KSC Library Already Initialized.";

        case FAIL_KSC_NOT_POWERED_ON:
            return "KSC Not Powered On.";
        case FAIL_KSC_ALREADY_POWERED_ON:
            return "KSC Already Powered On.";
        case FAIL_KSC_NOT_POWERED_OFF:
            return "KSC Not Powered Off.";
        case FAIL_KSC_ALREADY_POWERED_OFF:
            return "KSC Already Powered Off.";

        case FAIL_KSC_ATR:
            return "KSC ATR Error.";
        case FAIL_KSC_PPS:
            return "KSC PPS Error.";
        case FAIL_KSC_TX:
            return "KSC Tx Error.";
        case FAIL_KSC_RX:
            return "KSC Rx Error.";
        case FAIL_KSC_CHAINING:
            return "KSC Chaining Error.";
        case FAIL_KSC_APDU_FORMAT:
            return "KSC Wrong APDU Format.";

        case FAIL_UNKNOWN_CMD_TYPE:
            return "KeyWallet Unknown Command Type.";
        case FAIL_UNKNOWN_SMARTCARD_CMD:
            return "KeyWallet Unknown Smartcard Command.";

        default:
            return "Unknown Error Code (" + String.format("{0:x4}", usErrorCode) + ").";
        }

    }

    class DataTransferThread extends Thread {
        private byte[] buffer;
        private int endpointIndex;

        public DataTransferThread(byte[] bytes, int endpointIndex) {
            this.buffer = bytes;
            this.endpointIndex = endpointIndex;
        }

        @Override
        public void start() {
            mEndpoint = mInterface.getEndpoint(endpointIndex);
            Log.i(TAG, "direction: " + mEndpoint.getDirection());
            mConnection.bulkTransfer(mEndpoint, buffer, buffer.length, TIMEOUT);
            Log.d(TAG, "bytes transferred. - " + buffer);
            if (endpointIndex == 0) {
                m_abInReport = buffer;
                m_fInReportEmpty = false;
            }
        }
    }
}
