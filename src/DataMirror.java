/*
 * Copyright 2009-2015 Paolo Conte
 * This library is part of the Jelly framework.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jellylab.data;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * Generic class to map DataStruct to Java/J2EE objects (ResultSet, HTTPRequest, JSON string, Map)
 *
 * @author paoloc
 */
public class DataMirror
{

    public static final String LAST_PKID = "lastID";
    public static final int TIPO_QUERY_INSERT = 1;
    public static final int TIPO_QUERY_UPDATE = 2;
    private final String ENCODING = "ISO-8859-15";
    public static final String REQUEST_ERRORS_ONLOAD = "requestErrorsOnLoad";
    /**
     * force int value to zero
     */
    private final int ZERO_VALUE = -3699639;
    /**
     * force int value to null
     */
    private final int NULL_VALUE = -3699633;
    /**
     * main DataStruct object
     */
    private DataStruct dataStruct;

    /**
     * Empty constructor
     */
    public DataMirror()
    {
    }

    /**
     * Constructor with main DataStruct
     *
     * @param dataStruct
     */
    public DataMirror(DataStruct dataStruct)
    {
        this.dataStruct = dataStruct;
    }

    /**
     * Set the main DataStruct object
     * @param dataStruct
     * @return new instance of Mirror
     */
    public static DataMirror on(DataStruct dataStruct)
    {
        DataMirror mirror = new DataMirror(dataStruct);
        return mirror;
    }

    public void set(DataStruct dataStruct)
    {
        this.dataStruct = dataStruct;
    }

    public DataStruct getData()
    {
        return dataStruct;
    }

    /**
     * Convert DataStruct to map (synchronized HashMap)
     *
     * @return Map with key = field name, value = field value
     */
    public Map asMap()
    {
        Field[] fields = dataStruct.getFields();

        if (fields == null)
        {
            return null;
        }

        int fieldLen = fields.length;

        Map map = new HashMap(fieldLen);
        map = Collections.synchronizedMap(map);

        // DataStruct fields loop
        for (int idf = 0; idf < fieldLen; idf++)
        {
            Field field = fields[idf];
            field.setAccessible(true);
            String name = field.getName();
            Class type = field.getType();

            if (type.equals(String.class))
            {
                // continue on exception
                Object value = null;
                try
                {
                    value = field.get(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                map.put(name, value);
            }
            else if (type.equals(int.class))
            {
                int intVal = 0;
                try
                {
                    intVal = field.getInt(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                map.put(name, new Integer(intVal));
            }
            else if (type.equals(double.class))
            {
                double dblVal = 0;
                try
                {
                    dblVal = field.getDouble(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                map.put(name, new Double(dblVal));
            }
        }

        return map;
    }

    /**
     * Load the DataStruct 
     * @param request
     * @return
     */
    public DataMirror loadFromRequest(HttpServletRequest request)
    {
        loadFromRequest(request, false);
        return this;
    }

    public DataMirror loadFromRequest(HttpServletRequest request, boolean urldecode)
    {
        Enumeration enume = request.getParameterNames();
        Field[] dataFields = dataStruct.getFields();

        int errMaxSize = 10;
        String[][] errori = new String[errMaxSize][2];
        int errCount = 0;
        while (enume.hasMoreElements())
        {
            String name = (String) enume.nextElement();
            String value = request.getParameter(name);
            for (int idf = 0; idf < dataFields.length; idf++)
            {
                Field field = dataFields[idf];
                if (field.getName().equals(name))
                {
                    try
                    {
                        field.setAccessible(true);
                        Class type = field.getType();
                        if (type.equals(String.class))
                        {
                            if (!isNullOrEmpty(value))
                            {
                                if (urldecode)
                                {
                                    value = URLDecoder.decode(value, ENCODING);
                                }
                                field.set(dataStruct, value);
                            }
                        }
                        else if (type.equals(int.class))
                        {
                            int intVal = new Integer(value).intValue();
                            field.setInt(dataStruct, intVal);
                        }
                        else if (type.equals(double.class))
                        {
                            double dblVal = gdv(value);
                            field.setDouble(dataStruct, dblVal);
                        }
                    }
                    catch (Exception exc)
                    {
                        if (errCount < errMaxSize)
                        {
                            errori[errCount][0] = name;
                            errori[errCount][1] = value;
                        }
                        errCount++;
                    }
                }
            }
        }
        if (errCount > 0)
        {
            request.setAttribute(REQUEST_ERRORS_ONLOAD, errori);
        }

        return this;
    }

    public DataMirror loadFromResultSetRow(ResultSet rset)
    {
        Field[] fields = dataStruct.getFields();

        int fieldLen = fields.length;
        for (int idf = 0; idf < fieldLen; idf++)
        {
            Field field = fields[idf];
            String fieldName = field.getName().toUpperCase();
            field.setAccessible(true);
            Class type = field.getType();

            if (type.equals(String.class))
            {
                try
                {
                    String value = rset.getString(fieldName);
                    value = nvl(value);
                    field.set(dataStruct, value);
                }
                catch (Exception exc)
                {
                    continue;
                }
            }
            else if (type.equals(int.class))
            {
                try
                {
                    int value = rset.getInt(fieldName);
                    field.setInt(dataStruct, value);
                }
                catch (Exception exc)
                {
                    continue;
                }
            }
            else if (type.equals(double.class))
            {
                try
                {
                    double value = rset.getDouble(fieldName);
                    field.setDouble(dataStruct, value);
                }
                catch (Exception exc)
                {
                    continue;
                }
            }
        }

        return this;
    }

    public List<DataStruct> loadFromRequests(HttpServletRequest request)
    {
        Enumeration enume = request.getParameterNames();
        Field[] dataFields = dataStruct.getFields();
        final char fieldSep = '_';

        Map loadStructs = new HashMap();
        while (enume.hasMoreElements())
        {
            String name = (String) enume.nextElement();
            String value = request.getParameter(name);

            int posSep = name.indexOf(fieldSep);
            if (posSep < 0)
            {
                continue;
            }

            String param = name.substring(0, posSep);
            String pos = name.substring(posSep + 1, name.length());

            DataStruct struct = null;
            if (loadStructs.containsKey(pos))
            {
                struct = (DataStruct) loadStructs.get(pos);
            }
            else
            {
                struct = dataStruct.newInstance();
            }

            for (int idf = 0; idf < dataFields.length; idf++)
            {
                Field field = dataFields[idf];
                if (field.getName().equals(param))
                {
                    try
                    {
                        field.setAccessible(true);
                        Class type = field.getType();
                        if (type.equals(String.class))
                        {
                            if (!isNullOrEmpty(value))
                            {
                                field.set(struct, value);
                            }
                        }
                        else if (type.equals(int.class))
                        {
                            int intVal = new Integer(value).intValue();
                            field.setInt(struct, intVal);
                        }
                        else if (type.equals(double.class))
                        {
                            double dblVal = gdv(value);
                            field.setDouble(struct, dblVal);
                        }
                    }
                    catch (Exception exc)
                    {
                    }
                }
            }
            loadStructs.put(pos, struct);
        }

        return new ArrayList(loadStructs.values());
    }

    public String whereClause()
    {
        Field[] fields = dataStruct.getFields();

        if (fields == null)
        {
            return "";
        }

        int fieldLen = fields.length;

        StringBuffer where = new StringBuffer(1000);
        // loop sui campi della DataStruct
        for (int idf = 0; idf < fieldLen; idf++)
        {
            Field field = fields[idf];
            field.setAccessible(true);
            String name = field.getName();
            Class type = field.getType();

            StringBuffer whereField = new StringBuffer(50);
            if (type.equals(String.class))
            {
                Object value = null;
                try
                {
                    value = field.get(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (value == null)
                {
                    continue;
                }
                String strVal = value.toString();
                if (!strVal.equals(""))
                {
                    whereField.append(name.toUpperCase());
                    whereField.append(" = ?");
                }
            }
            else if (type.equals(int.class))
            {
                int intVal = 0;
                try
                {
                    intVal = field.getInt(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (intVal != 0 || intVal == ZERO_VALUE || intVal == NULL_VALUE)
                {
                    whereField.append(name.toUpperCase());
                    whereField.append(" = ?");
                }
            }
            else if (type.equals(double.class))
            {
                double dblVal = 0;
                try
                {
                    dblVal = field.getDouble(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (dblVal != 0 || dblVal == ZERO_VALUE || dblVal == NULL_VALUE)
                {
                    whereField.append(name.toUpperCase());
                    whereField.append(" = ?");
                }
            }

            if (whereField.length() > 0)
            {
                if (where.length() > 0)
                {
                    where.append(" AND ");
                }
                where.append(whereField);
            }
        }

        return where.toString();
    }

    public PreparedStatement prepare(PreparedStatement prstm) throws SQLException
    {
        return prepare(prstm, false);
    }

    public PreparedStatement prepare(PreparedStatement prstm, boolean escludiPKID) throws SQLException
    {
        Field[] fields = dataStruct.getFields();

        int fieldLen = fields.length;
        int pos = 1;
        for (int idf = 0; idf < fieldLen; idf++)
        {
            Field field = fields[idf];
            field.setAccessible(true);

            if (escludiPKID)
            {
                String name = field.getName();

                // FIXME special case:
                if (name.toUpperCase().indexOf("PKID") > 0)
                {
                    continue;
                }
            }

            Class type = field.getType();

            if (type.equals(String.class))
            {
                Object value = null;
                try
                {
                    value = field.get(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (value == null)
                {
                    continue;
                }
                String strVal = value.toString();
                if (!strVal.equals(""))
                {
                    // FIXME special case
                    if (strVal.length() == 10 && strVal.indexOf('/') == 2 && strVal.lastIndexOf('/') == 5)
                    {
                        strVal = dtIT2DB(strVal);
                    }
                    prstm.setString(pos++, strVal);
                }
            }
            else if (type.equals(int.class))
            {
                int intVal = 0;
                try
                {
                    intVal = field.getInt(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (intVal != 0)
                {
                    if (intVal == ZERO_VALUE)
                    {
                        intVal = 0;
                        prstm.setInt(pos++, intVal);
                    }
                    else if (intVal == NULL_VALUE)
                    {
                        prstm.setNull(pos++, java.sql.Types.INTEGER);
                    }
                    else
                    {
                        prstm.setInt(pos++, intVal);
                    }
                }
            }
            else if (type.equals(double.class))
            {
                double dblVal = 0;
                try
                {
                    dblVal = field.getDouble(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (dblVal != 0)
                {
                    if (dblVal == ZERO_VALUE)
                    {
                        dblVal = 0;
                        prstm.setDouble(pos++, dblVal);
                    }
                    else if (dblVal == NULL_VALUE)
                    {
                        prstm.setNull(pos++, java.sql.Types.DOUBLE);
                    }
                    else
                    {
                        prstm.setDouble(pos++, dblVal);
                    }
                }
            }
        }

        return prstm;
    }

    public String asQueryString()
    {
        return asQueryString(false);
    }

    public String asQueryString(boolean urlencode)
    {
        Field[] fields = dataStruct.getFields();

        if (fields == null)
        {
            return "";
        }

        int fieldLen = fields.length;

        String qstring = "";

        for (int idf = 0; idf < fieldLen; idf++)
        {
            Field field = fields[idf];
            field.setAccessible(true);
            String name = field.getName();
            Class type = field.getType();

            // se di tipo stringa
            if (type.equals(String.class))
            {
                String strVal = null;
                try
                {
                    strVal = (String) field.get(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (strVal != null && !strVal.equals(""))
                {
                    if (urlencode)
                    {
                        try
                        {
                            strVal = URLEncoder.encode(strVal, ENCODING);
                        }
                        catch (UnsupportedEncodingException unencexc)
                        {
                            // encoding unknown for JVM
                            strVal = "*** Encoding error *** " + unencexc.getMessage();
                        }
                    }
                    qstring += "&" + name + "=" + strVal;
                }
            }
            else if (type.equals(int.class))
            {
                int intVal = 0;
                try
                {
                    intVal = field.getInt(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (intVal != 0)
                {
                    qstring += "&" + name + "=" + intVal;
                }
            }
            else if (type.equals(double.class))
            {
                double dblVal = 0;
                try
                {
                    dblVal = field.getDouble(dataStruct);
                }
                catch (Exception exc)
                {
                    continue;
                }
                if (dblVal != 0)
                {
                    qstring += "&" + name + "=" + dblVal;
                }
            }
        }

        return qstring;
    }

    public String asJSON()
    {
        Field[] fields = dataStruct.getFields();

        if (fields == null)
        {
            return "";
        }

        int fieldLen = fields.length;

        String json = "{";

        for (int idf = 0; idf < fieldLen; idf++)
        {
            Field field = fields[idf];
            field.setAccessible(true);
            String name = field.getName();
            Class type = field.getType();

            Object value = null;
            try
            {
                value = field.get(dataStruct);
            }
            catch (Exception exc)
            {
                continue;
            }

            if (value == null)
            {
                continue;
            }

            String strVal = "";
            if (type.equals(String.class))
            {
                strVal = (String) value;
                strVal = escape(strVal);
            }
            else if (type.equals(int.class) || type.equals(double.class))
            {
                strVal = "" + value;
            }

            json += "\"" + name + "\":\"" + strVal + "\"";

            if (idf < fieldLen - 1)
            {
                json += ", ";
            }
        }

        json += "}";

        return json;
    }

    /**
     * Remove null string
     * @param str String to check
     * @return "" if string is null (or "null"), otherwise parameter string
     */
    private String nvl(String str)
    {
        if (isNullOrEmpty(str))
        {
            return "";
        }
        return str;
    }

    /**
     * Get Double Value, from string
     * @param str String to parse
     * @return double from string, if parsable, else 0
     */
    private double gdv(String str)
    {
        double value = 0;
        boolean found = false;
        if (str == null || str.equals(""))
        {
            return 0;
        }

        try
        {
            // prova la conversione standard
            value = Double.parseDouble(str);
            found = true;
        }
        catch (Exception exc)
        {
        }

        if (!found)
        {
            NumberFormat nf = NumberFormat.getInstance(Locale.ITALIAN);
            try
            {
                // FIXME specific locale IT:
                value = nf.parse(str).doubleValue();
                found = true;
            }
            catch (ParseException pex)
            {
            }
        }

        return value;
    }

    private boolean isNullOrEmpty(String parameter)
    {
        if (parameter == null
                || parameter.trim().equals("")
                || parameter.equalsIgnoreCase("null")
                || parameter.equalsIgnoreCase("undefined"))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private String escape(String str)
    {
        if (str == null)
        {
            return "";
        }
        String ret = "";
        int sz;
        sz = str.length();
        for (int i = 0; i < sz; i++)
        {
            char ch = str.charAt(i);

            // handle unicode
            if (ch > 0xfff)
            {
                ret += "\\u" + hex(ch);
            }
            else if (ch > 0xff)
            {
                ret += "\\u0" + hex(ch);
            }
            else if (ch > 0x7f)
            {
                ret += "\\u00" + hex(ch);
            }
            else if (ch < 32)
            {
                switch (ch)
                {
                    case '\b':
                        ret += '\\';
                        ret += 'b';
                        break;
                    case '\n':
                        ret += '\\';
                        ret += 'n';
                        break;
                    case '\t':
                        ret += '\\';
                        ret += 't';
                        break;
                    case '\f':
                        ret += '\\';
                        ret += 'f';
                        break;
                    case '\r':
                        ret += '\\';
                        ret += 'r';
                        break;
                    default:
                        if (ch > 0xf)
                        {
                            ret += "\\u00" + hex(ch);
                        }
                        else
                        {
                            ret += "\\u000" + hex(ch);
                        }
                        break;
                }
            }
            else
            {
                switch (ch)
                {
                    case '\'':
                        ret += '\\';
                        ret += '\'';
                        break;
                    case '"':
                        ret += '\\';
                        ret += '"';
                        break;
                    case '\\':
                        ret += '\\';
                        ret += '\\';
                        break;
                    case '/':
                        ret += '\\';
                        ret += '/';
                        break;
                    default:
                        ret += ch;
                        break;
                }
            }

        }
        return ret;
    }

    private String hex(char ch)
    {
        return Integer.toHexString(ch).toUpperCase();
    }

    private String dtIT2DB(String dateIT)
    {
        Date data = dtIT2Date(dateIT);

        return date2DBdt(data);
    }

    private Date dtIT2Date(String sdate)
    {
        final String FORMATO_IT = "dd/MM/yyyy";
        SimpleDateFormat sdfmt = new SimpleDateFormat(FORMATO_IT);
        Date data = null;
        try
        {
            data = sdfmt.parse(sdate);
        }
        catch (ParseException pexc)
        {
        }
        catch (NullPointerException npexc)
        {
        }

        return data;
    }

    private String date2DBdt(Date date)
    {
        if (date == null)
        {
            return "";
        }

        final String FORMATO_DB = "yyyyMMddHHmmss";

        SimpleDateFormat sdfmt = new SimpleDateFormat(FORMATO_DB);
        String sdata = null;
        sdata = sdfmt.format(date);

        return sdata;
    }

    /**
     * Generic data struct
     */
    public static class DataStruct
    {

        private transient Field[] fields = this.getClass().getFields();
        private transient String name = this.getClass().getName();
        private int orderBy = -1;

        /**
         * All fields, ordered by name
         */
        public Field[] getFields()
        {
            Arrays.sort(fields, new FieldComparator());
            return fields;
        }

        public String getSingleName()
        {
            return name.substring(name.indexOf("$") + 1);
        }

        public int getOrderBy()
        {
            return orderBy;
        }

        public void setOrderBy(int orderBy)
        {
            this.orderBy = orderBy;
        }

        public DataStruct newInstance()
        {
            try
            {
                return (DataStruct) this.getClass().newInstance();
            }
            catch (InstantiationException inexc)
            {
                return null;
            }
            catch (IllegalAccessException illexc)
            {
                return null;
            }
        }

        public String toString()
        {
            String strVal = "[" + getSingleName() + "]";
            for (int idf = 0; idf < fields.length; idf++)
            {
                Field field = fields[idf];
                field.setAccessible(true);
                if (idf > 0)
                {
                    strVal += ", ";
                }
                strVal += field.getName() + ":";
                try
                {
                    strVal += field.get(this);
                }
                catch (Exception exc)
                {
                    strVal += "{error}";
                }
            }
            return strVal;
        }
    }

    /**
     * Comparator for Field object, by name ASC
     */
    public static class FieldComparator implements Comparator
    {

        public int compare(Object obj1, Object obj2)
        {
            Field field1 = (Field) obj1;
            Field field2 = (Field) obj2;
            String fieldName1 = field1.getName().toUpperCase();
            String fieldName2 = field2.getName().toUpperCase();

            // ASC order:
            return fieldName1.compareTo(fieldName2);
        }
    }
}
