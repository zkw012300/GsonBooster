package com.zspirytus.booster.processor.data.type

import com.squareup.kotlinpoet.*
import com.zspirytus.booster.processor.const.TYPE_ADAPTER_FIELD_NAME_SUFFIX
import javax.lang.model.element.Element

data class BackoffKType(
    val element: Element
) : KType(element) {
    override val adapterFieldName: String
        get() = _adapterFieldName

    private val _adapterFieldName: String by lazy {
        getAdapterFieldName(typeName)
    }

    private fun getAdapterFieldName(typeName: TypeName): String {
        var adapterFieldName = getAdapterFieldNameRecursively(typeName).joinToString("")
        adapterFieldName = "$adapterFieldName$TYPE_ADAPTER_FIELD_NAME_SUFFIX"
        return adapterFieldName.replaceFirst(
            adapterFieldName.first(),
            adapterFieldName.first().toLowerCase()
        )
    }

    private fun getAdapterFieldNameRecursively(typeName: TypeName): List<String> {
        val names = mutableListOf<String>()
        when (typeName) {
            is ClassName -> {
                names.add(typeName.simpleName)
            }
            is ParameterizedTypeName -> {
                names.add(typeName.rawType.simpleName)
                typeName.typeArguments.forEach {
                    names.addAll(getAdapterFieldNameRecursively(it))
                }
            }
            is WildcardTypeName -> {
                // 协变逆变
                val inTypeNames = typeName.inTypes.map {
                    getAdapterFieldNameRecursively(it).joinToString("")
                }
                val outTypeNames = typeName.outTypes.map {
                    getAdapterFieldNameRecursively(it).joinToString("")
                }
                names.addAll(inTypeNames)
                names.addAll(outTypeNames)
            }
            is Dynamic -> {
                // 动态类型，忽略
            }
            is LambdaTypeName -> {
                // lambda类型，忽略
            }
            is TypeVariableName -> {
                // 泛型名称
                names.add(typeName.name)
            }
        }
        return names
    }
}