package com.orbital.automation

enum class FormFieldType {
    FULL_NAME,
    FIRST_NAME,
    LAST_NAME,
    EMAIL_ADDRESS,
    PHONE_NUMBER,
    STREET_ADDRESS,
    CITY,
    POSTAL_CODE,
    COUNTRY,
    SEARCH_QUERY,
    UNKNOWN
}

data class AutofillPlanItem(
    val element: UIElement,
    val fieldType: FormFieldType,
    val valueToFill: String
)

data class AutofillPlan(
    val targetPackage: String,
    val items: List<AutofillPlanItem>
)

object SmartAutofillEngine {

    fun classifyField(element: UIElement): FormFieldType {
        if (!element.isEditable) return FormFieldType.UNKNOWN

        val textLower = element.text.lowercase()
        val descLower = element.contentDescription?.lowercase() ?: ""
        val idLower = element.viewId?.lowercase() ?: ""
        val combined = "$textLower $descLower $idLower"

        return when {
            combined.contains("first name") || combined.contains("fname") || combined.contains("given name") -> FormFieldType.FIRST_NAME
            combined.contains("last name") || combined.contains("lname") || combined.contains("surname") -> FormFieldType.LAST_NAME
            combined.contains("name") || combined.contains("fullname") -> FormFieldType.FULL_NAME
            combined.contains("email") || combined.contains("e-mail") || combined.contains("mail") -> FormFieldType.EMAIL_ADDRESS
            combined.contains("phone") || combined.contains("mobile") || combined.contains("tel") -> FormFieldType.PHONE_NUMBER
            combined.contains("zip") || combined.contains("postal") || combined.contains("pincode") || combined.contains("pin code") -> FormFieldType.POSTAL_CODE
            combined.contains("city") || combined.contains("town") -> FormFieldType.CITY
            combined.contains("address") || combined.contains("street") || combined.contains("addr") -> FormFieldType.STREET_ADDRESS
            combined.contains("country") || combined.contains("nation") -> FormFieldType.COUNTRY
            combined.contains("search") || combined.contains("find") || combined.contains("query") -> FormFieldType.SEARCH_QUERY
            else -> FormFieldType.UNKNOWN
        }
    }

    fun generateAutofillPlan(
        snapshot: ScreenHierarchySnapshot,
        profileData: Map<FormFieldType, String>
    ): AutofillPlan {
        val planItems = mutableListOf<AutofillPlanItem>()

        snapshot.elements.filter { it.isEditable }.forEach { el ->
            val fieldType = classifyField(el)
            val value = profileData[fieldType]
            if (value != null && value.isNotBlank()) {
                planItems.add(AutofillPlanItem(el, fieldType, value))
            }
        }

        return AutofillPlan(
            targetPackage = snapshot.packageName,
            items = planItems
        )
    }
}
