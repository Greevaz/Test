/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package top.yztz.gingerbulkmessenger.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores user-named message templates in their own SharedPreferences file, so that
 * "clear cache and history" in Settings does not remove them.
 */
public class TemplateManager {
    private static final String PREF_NAME = "template_prefs";
    private static final String KEY_TEMPLATES = "saved_templates_v1";

    public static class SavedTemplate {
        public final String name;
        public final String content;
        public final long timestamp;

        public SavedTemplate(String name, String content, long timestamp) {
            this.name = name;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    /** Newest first. */
    public static List<SavedTemplate> getTemplates(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = sp.getString(KEY_TEMPLATES, "");
        List<SavedTemplate> list = new ArrayList<>();
        if (!TextUtils.isEmpty(jsonStr)) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new SavedTemplate(
                            obj.optString("name"),
                            obj.optString("content"),
                            obj.optLong("timestamp")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public static boolean exists(Context context, String name) {
        for (SavedTemplate t : getTemplates(context)) {
            if (t.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Saves a template; a template with the same name (case-insensitive) is replaced. */
    public static void saveTemplate(Context context, String name, String content) {
        if (TextUtils.isEmpty(name)) return;
        List<SavedTemplate> list = getTemplates(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(name)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, new SavedTemplate(name, content, System.currentTimeMillis()));
        write(context, list);
    }

    public static void deleteTemplate(Context context, String name) {
        List<SavedTemplate> list = getTemplates(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(name)) {
                list.remove(i);
                break;
            }
        }
        write(context, list);
    }

    private static void write(Context context, List<SavedTemplate> list) {
        JSONArray arr = new JSONArray();
        try {
            for (SavedTemplate t : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", t.name);
                obj.put("content", t.content);
                obj.put("timestamp", t.timestamp);
                arr.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_TEMPLATES, arr.toString()).apply();
    }
}
