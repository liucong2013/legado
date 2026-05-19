package io.legado.app.ui.config

import android.os.Bundle
import android.text.Editable
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.google.gson.reflect.TypeToken
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.model.entities.AiConfigProfile
import io.legado.app.utils.GSON
import io.legado.app.utils.showEditTextDialog
import io.legado.app.utils.toastOnUi

class AiSummaryConfigFragment : PreferenceFragment() {

    private var profiles = mutableListOf<AiConfigProfile>()
    private var activeProfile: AiConfigProfile? = null

    private lateinit var activeProfilePref: ListPreference
    private lateinit var apiKeyPref: EditTextPreference
    private lateinit var apiUrlPref: EditTextPreference
    private lateinit var apiFormatPref: ListPreference
    private lateinit var modelIdPref: EditTextPreference
    private lateinit var systemPromptPref: EditTextPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_ai_summary_config, rootKey)
        setupProfiles()
        setupListeners()
    }

    private fun setupProfiles() {
        loadProfiles()
        bindPreferences()
        updateActiveProfile()
        updateProfileListPref()
        updateUiWithActiveProfile()
    }

    private fun bindPreferences() {
        activeProfilePref = findPreference("aiActiveProfileName")!!
        apiKeyPref = findPreference("ai_profile_api_key")!!
        apiUrlPref = findPreference("ai_profile_api_url")!!
        apiFormatPref = findPreference("ai_profile_api_format")!!
        modelIdPref = findPreference("ai_profile_model_id")!!
        systemPromptPref = findPreference("ai_profile_system_prompt")!!
    }

    private fun loadProfiles() {
        val profilesJson = AppConfig.aiConfigProfiles
        profiles = if (profilesJson.isNullOrEmpty()) {
            mutableListOf(AiConfigProfile(name = "默认"))
        } else {
            try {
                GSON.fromJson(profilesJson, object : TypeToken<MutableList<AiConfigProfile>>() {}.type)
            } catch (e: Exception) {
                mutableListOf(AiConfigProfile(name = "默认"))
            }
        }
    }

    private fun saveProfiles() {
        AppConfig.aiConfigProfiles = GSON.toJson(profiles)
    }

    private fun updateActiveProfile() {
        val activeName = AppConfig.aiActiveProfileName
        activeProfile = profiles.firstOrNull { it.name == activeName } ?: profiles.first()
        AppConfig.aiActiveProfileName = activeProfile?.name
    }

    private fun updateProfileListPref() {
        val profileNames = profiles.map { it.name }.toTypedArray()
        activeProfilePref.entries = profileNames
        activeProfilePref.entryValues = profileNames
        activeProfilePref.value = activeProfile?.name
    }

    private fun updateUiWithActiveProfile() {
        activeProfile?.let {
            apiKeyPref.text = it.apiKey
            apiUrlPref.text = it.apiUrl
            apiFormatPref.value = it.apiFormat
            modelIdPref.text = it.modelId
            systemPromptPref.text = it.systemPrompt
        }
    }

    private fun setupListeners() {
        activeProfilePref.setOnPreferenceChangeListener { _, newValue ->
            AppConfig.aiActiveProfileName = newValue as String
            updateActiveProfile()
            updateUiWithActiveProfile()
            true
        }

        val detailPrefs = listOf(apiKeyPref, apiUrlPref, apiFormatPref, modelIdPref, systemPromptPref)
        detailPrefs.forEach { pref ->
            pref.setOnPreferenceChangeListener { _, newValue ->
                activeProfile?.let {
                    when (pref.key) {
                        "ai_profile_api_key" -> it.apiKey = newValue as String
                        "ai_profile_api_url" -> it.apiUrl = newValue as String
                        "ai_profile_api_format" -> it.apiFormat = newValue as String
                        "ai_profile_model_id" -> it.modelId = newValue as String
                        "ai_profile_system_prompt" -> it.systemPrompt = newValue as String
                    }
                    saveProfiles()
                }
                true
            }
        }

        findPreference<Preference>("ai_profile_add")?.setOnPreferenceClickListener {
            showEditTextDialog(R.string.ai_profile_add) { it: Editable ->
                val newName = it.toString().trim()
                if (newName.isNotEmpty() && profiles.none { p -> p.name == newName }) {
                    profiles.add(AiConfigProfile(name = newName))
                    saveProfiles()
                    AppConfig.aiActiveProfileName = newName
                    setupProfiles() // Refresh everything
                } else {
                    toastOnUi("名称不能为空或重复")
                }
            }
            true
        }

        findPreference<Preference>("ai_profile_rename")?.setOnPreferenceClickListener {
            activeProfile?.let { profile ->
                showEditTextDialog(R.string.ai_profile_rename, default = profile.name) { it: Editable ->
                    val newName = it.toString().trim()
                    if (newName.isNotEmpty() && profiles.none { p -> p.name == newName }) {
                        profile.name = newName
                        AppConfig.aiActiveProfileName = newName
                        saveProfiles()
                        setupProfiles() // Refresh everything
                    } else {
                        toastOnUi("名称不能为空或重复")
                    }
                }
            }
            true
        }

        findPreference<Preference>("ai_profile_delete")?.setOnPreferenceClickListener {
            if (profiles.size <= 1) {
                toastOnUi("至少需要保留一个配置方案")
                return@setOnPreferenceClickListener true
            }
            activeProfile?.let { profile ->
                AlertDialog.Builder(requireContext())
                    .setTitle("确认删除 ${profile.name}?")
                    .setMessage(R.string.ai_profile_delete_desc)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        profiles.remove(profile)
                        saveProfiles()
                        AppConfig.aiActiveProfileName = profiles.first().name
                        setupProfiles() // Refresh everything
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            true
        }
    }
}