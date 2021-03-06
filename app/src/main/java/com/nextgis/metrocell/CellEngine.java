/*
 * Project:  Metrocell
 * Purpose:  Locating in metro by cell towers
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextgis.metrocell;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import com.nextgis.metrocell.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class CellEngine {
    public final static int SIGNAL_STRENGTH_NONE = 0;

    private final static int LOW_BOUND = 0;
    private final static int MAX_MCC_MNC = 999;
    private final static int MAX_2G_LAC_CID = 65535;
    private final static int MAX_3G_CID = 268435455;
    private final static int MAX_PSC = 511;

    Context mContext;

    private TelephonyManager mTelephonyManager;
    private GSMPhoneStateListener mSignalListener;
    private int signalStrength = SIGNAL_STRENGTH_NONE;

    private List<CellInfoListener> mCellListeners;

    interface CellInfoListener {
        public void onCellInfoChanged();
    }

    public CellEngine(Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mSignalListener = new GSMPhoneStateListener();
        mCellListeners = new ArrayList<>();
    }

    public void addCellListener(CellInfoListener listener) {
        mCellListeners.add(listener);
    }

    private void notifyCellListeners() {
        for (CellInfoListener listener : mCellListeners)
            listener.onCellInfoChanged();
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public void onResume() {
        int listen = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS;

        if (mCellListeners.size() > 0) {
            listen |= PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_SERVICE_STATE;
        }

        mTelephonyManager.listen(mSignalListener, listen);
    }

    public void onPause() {
        mTelephonyManager.listen(mSignalListener, PhoneStateListener.LISTEN_NONE);
    }

    //    private void onCellLocationChanged(CellLocation location) {
    //
    //        GsmCellLocation gsmCellLocation;
    //        try {
    //            gsmCellLocation = (GsmCellLocation) location;
    //        } catch (ClassCastException e) {
    //            gsmCellLocation = null;
    //        }
    //
    //        if (gsmCellLocation != null) {
    ////            gsmCellLocation.getCid(), gsmCellLocation.getLac();
    //        }
    //    }

    private class GSMPhoneStateListener extends PhoneStateListener {

        /*
         * Get the Signal strength from the provider, each time there is an
         * update
         */
        @Override
        public void onSignalStrengthsChanged(SignalStrength signal) {
            super.onSignalStrengthsChanged(signal);
            setSignalStrength(signal.isGsm() ? signalStrengthAsuToDbm(signal.getGsmSignalStrength(), TelephonyManager.NETWORK_TYPE_GPRS) : SIGNAL_STRENGTH_NONE);
            notifyCellListeners();
        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            super.onCellLocationChanged(location);
            notifyCellListeners();
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            super.onDataConnectionStateChanged(state, networkType);
            notifyCellListeners();
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            super.onServiceStateChanged(serviceState);
            notifyCellListeners();
        }
    }

    public int signalStrengthAsuToDbm(int asu, int networkType) {

        switch (networkType) {

            // 2G -- GSM network
            case TelephonyManager.NETWORK_TYPE_GPRS: // API 1+
            case TelephonyManager.NETWORK_TYPE_EDGE: // API 1+
                // getRssi() returns ASU for GSM
                if (0 <= asu && asu <= 31) {
                    return 2 * asu - 113;
                } else if (asu == 99) {
                    return 0;
                }
                break;

            //	3G -- UMTS network
            case TelephonyManager.NETWORK_TYPE_UMTS: // API 1+
            case TelephonyManager.NETWORK_TYPE_HSPA: // API 5+
            case TelephonyManager.NETWORK_TYPE_HSDPA: // API 5+
            case TelephonyManager.NETWORK_TYPE_HSUPA: // API 5+
            case TelephonyManager.NETWORK_TYPE_HSPAP: // API 5+
                // getRssi() returns RSCP for UMTS
                //			if (-5 <= asu && asu <= 91) {
                //				return asu - 116;
                //			} else if (asu == 255) {
                //				return 0;
                //			}
                return asu;
            //			break;

            // 4G -- LTE network
            //            case TelephonyManager.NETWORK_TYPE_LTE : // API 11+
            //                if (0 <= asu && asu <= 97) {
            //                    return asu - 141;
            //                }
            //                break;
        }

        return 0;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public ArrayList<GSMInfo> getGSMInfoArray() {
        int osVersion = Build.VERSION.SDK_INT;
        int api17 = Build.VERSION_CODES.JELLY_BEAN_MR1;
        int api18 = Build.VERSION_CODES.JELLY_BEAN_MR2;

        ArrayList<GSMInfo> gsmInfoArray = new ArrayList<>();

        long timeStamp = System.currentTimeMillis();
//		boolean useAPI17 = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Constants.PREF_USE_API17, false); // WCDMA uses API 18+, now min is 18
        boolean useAPI17 = true;
        boolean isRegistered = false;

        // #2 using API 17 to get all cell towers around, including one which phone registered to
        if (osVersion >= api17 && useAPI17) {
            List<CellInfo> allCells = mTelephonyManager.getAllCellInfo();
            int nwType = mTelephonyManager.getNetworkType();

            if (allCells != null) // Samsung and Nexus return null
                for (CellInfo cell : allCells) {
                    // 2G - GSM cell towers only
                    if (cell.getClass() == CellInfoGsm.class) {
                        CellInfoGsm gsm = (CellInfoGsm) cell;
                        CellIdentityGsm gsmIdentity = gsm.getCellIdentity();

                        isRegistered |= gsm.isRegistered();
                        gsmInfoArray.add(new GSMInfo(timeStamp, gsm.isRegistered(), nwType, gsmIdentity.getMcc(), gsmIdentity.getMnc(), gsmIdentity.getLac(),
                                gsmIdentity.getCid(), Constants.UNDEFINED, gsm.getCellSignalStrength().getDbm()));

                        // 3G - WCDMA cell towers, its API 18+
                    } else if (osVersion >= api18 && cell.getClass() == CellInfoWcdma.class) {
                        CellInfoWcdma wcdma = (CellInfoWcdma) cell;
                        CellIdentityWcdma wcdmaIdentity = wcdma.getCellIdentity();

                        isRegistered |= wcdma.isRegistered();
                        gsmInfoArray.add(new GSMInfo(timeStamp, wcdma.isRegistered(), nwType, wcdmaIdentity.getMcc(), wcdmaIdentity.getMnc(), wcdmaIdentity
                                .getLac(), wcdmaIdentity.getCid(), wcdmaIdentity.getPsc(), wcdma.getCellSignalStrength().getDbm()));
                    }
                }
        }

        if (gsmInfoArray.size() == 0) { // in case API 17/18 didn't return anything
            // #1 using default way to obtain cell towers info
            int mcc = Constants.UNDEFINED;
            int mnc = Constants.UNDEFINED;

            //			boolean isNetworkTypeGSM = isGSMNetwork(mTelephonyManager.getNetworkType());
            boolean isPhoneTypeGSM = mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
            String networkOperator = mTelephonyManager.getNetworkOperator();

            // getNetworkOperator() will return an empty string if a SIM card is not inserted
            // or if user's sim card is not registered at any operator's cell
            if (networkOperator != null && networkOperator.length() > 0 && isPhoneTypeGSM) {
                mcc = Integer.parseInt(networkOperator.substring(0, 3));
                mnc = Integer.parseInt(networkOperator.substring(3));
            }

            if (isPhoneTypeGSM) {
                GsmCellLocation gsmCellLocation;

                try {
                    gsmCellLocation = (GsmCellLocation) mTelephonyManager.getCellLocation();
                } catch (ClassCastException e) {
                    gsmCellLocation = null;
                }

                if (gsmCellLocation != null) {
                    isRegistered = true;
                    gsmInfoArray.add(new GSMInfo(timeStamp, true, mTelephonyManager.getNetworkType(), mcc, mnc, gsmCellLocation.getLac(), gsmCellLocation
                            .getCid(), gsmCellLocation.getPsc(), signalStrength));
                }
            }

            List<NeighboringCellInfo> neighbors = mTelephonyManager.getNeighboringCellInfo();

            for (NeighboringCellInfo neighbor : neighbors) {
                int nbNetworkType = neighbor.getNetworkType();

                //				if (nbNetworkType == TelephonyManager.NETWORK_TYPE_GPRS || nbNetworkType == TelephonyManager.NETWORK_TYPE_EDGE) {
                gsmInfoArray.add(new GSMInfo(timeStamp, false, nbNetworkType, Constants.UNDEFINED, Constants.UNDEFINED, neighbor.getLac(), neighbor.getCid(), neighbor.getPsc(),
                        signalStrengthAsuToDbm(neighbor.getRssi(), nbNetworkType)));
                //				}
            }
        }

        if (gsmInfoArray.size() == 0 || !isRegistered) { // add default record if there is no items in array /-1
            gsmInfoArray.add(new GSMInfo(timeStamp));
        }

        return gsmInfoArray;
    }

    public String getNetworkOperator() {
        return mTelephonyManager.getNetworkOperatorName();
    }

    //	public static boolean isGSMNetwork(int network) {
    //		return network == TelephonyManager.NETWORK_TYPE_EDGE || network == TelephonyManager.NETWORK_TYPE_GPRS;
    //	}

    public static String getNetworkGen(int type) {
        String gen;

        switch (type) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
                gen = "2G";
                break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                gen = "3G";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                gen = "4G";
                break;
            default:
                gen = "unknown";
                break;
        }

        return gen;
    }

    public static String getNetworkType(int type) {
        String network;

        switch (type) {
            //		case TelephonyManager.NETWORK_TYPE_CDMA:
            //			break;
            //		case TelephonyManager.NETWORK_TYPE_EVDO_0:
            //			break;
            //		case TelephonyManager.NETWORK_TYPE_IDEN:
            //			break;
            case TelephonyManager.NETWORK_TYPE_EDGE:
                network = "EDGE";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
                network = "GPRS";
                break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
                network = "UMTS";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPA:
                network = "HSPA";
                break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                network = "HSDPA";
                break;
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                network = "HSUPA";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                network = "HSPAP";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                network = "LTE";
                break;
            //		case TelephonyManager.NETWORK_TYPE_UNKNOWN:
            //			break;
            default:
                network = "unknown";
                break;
        }

        return network;
    }

    public static String getItem(GSMInfo gsmInfo, String active, String ID, String markName, String userName) {
        StringBuilder sb = new StringBuilder();

        sb.append(ID).append(Constants.CSV_SEPARATOR);
        sb.append(markName).append(Constants.CSV_SEPARATOR);
        sb.append(userName).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.getTimeStamp()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.networkGen()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.networkType()).append(Constants.CSV_SEPARATOR);
        sb.append(active).append(Constants.CSV_SEPARATOR);

        sb.append(gsmInfo.getMcc()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.getMnc()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.getLac()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.getCid()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.getPsc()).append(Constants.CSV_SEPARATOR);
        sb.append(gsmInfo.getRssi());

        return sb.toString();
    }

    public class GSMInfo {
        private long timeStamp;
        private boolean active;
        private int mcc;
        private int mnc;
        private int lac;
        private int cid;
        private int psc;
        private int networkType;
        private int rssi;
        private int minPower, maxPower;

        public GSMInfo(long timeStamp) {
            this.timeStamp = timeStamp;
            this.active = true;
            this.networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            this.mcc = Constants.UNDEFINED;
            this.mnc = Constants.UNDEFINED;
            this.lac = Constants.UNDEFINED;
            this.cid = Constants.UNDEFINED;
            this.psc = Constants.UNDEFINED;
            this.rssi = Constants.UNDEFINED;
        }

        public GSMInfo(long timeStamp, boolean active, int networkType, int mcc, int mnc, int lac, int cid, int psc, int rssi) {
            this.timeStamp = timeStamp;
            this.active = active;
            this.networkType = networkType;
            this.lac = lac;
            this.cid = cid;
            this.psc = psc;
            this.rssi = minPower = maxPower = rssi;

            boolean outOfBounds = mcc <= LOW_BOUND || mcc >= MAX_MCC_MNC;
            this.mcc = outOfBounds ? Constants.UNDEFINED : mcc;

            outOfBounds = mnc <= LOW_BOUND || mnc >= MAX_MCC_MNC;
            this.mnc = outOfBounds ? Constants.UNDEFINED : mnc;

            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    if (lac <= LOW_BOUND || lac >= MAX_2G_LAC_CID)
                        this.lac = -1;

                    if (cid <= LOW_BOUND || cid >= MAX_2G_LAC_CID)
                        this.cid = -1;
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                default:
                    if (lac <= LOW_BOUND || lac >= MAX_2G_LAC_CID) {
                            this.lac = -1;
                    }

                    if (cid <= LOW_BOUND || cid >= MAX_3G_CID) {
                            this.cid = -1;
                    }

                    if (psc <= LOW_BOUND || psc >= MAX_PSC) {
                            this.psc = -1;
                    }
                    break;
            }
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public boolean isActive() {
            return active;
        }

        public String networkType() {
            return getNetworkType(networkType);
        }

        public String networkGen() {
            return getNetworkGen(networkType);
        }

        public int getMcc() {
            return mcc;
        }

        public int getMnc() {
            return mnc;
        }

        public int getLac() {
            return lac;
        }

        public int getCid() {
            return cid;
        }

        public int getPsc() {
            return psc;
        }

        public int getRssi() {
            return rssi;
        }

        public int getMinPower() {
            return minPower;
        }

        public int getMaxPower() {
            return maxPower;
        }

        public void setMinPower(int minPower) {
            this.minPower = minPower;
        }

        public void setMaxPower(int maxPower) {
            this.maxPower = maxPower;
        }

        public boolean equals(GSMInfo item) {
            return item.getCid() == cid && item.getLac() == lac;
        }
    }
}
