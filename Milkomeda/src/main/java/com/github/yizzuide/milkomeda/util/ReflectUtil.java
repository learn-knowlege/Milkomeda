package com.github.yizzuide.milkomeda.util;

import com.github.yizzuide.milkomeda.universe.el.ELContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ReflectUtil
 * 反射工具类
 *
 * @author yizzuide
 * @since 0.2.0
 * @version 3.4.0
 * Create at 2019/04/11 19:55
 */
@Slf4j
public class ReflectUtil {

    /**
     * 获取父类或接口上泛型对应的Class
     * @param type  泛型原型
     * @return Class[]
     */
    public static Class<?>[] getClassOfParameterizedType(Type type) {
        // 是否带泛型
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType)type;
            Type[] subTypes = pt.getActualTypeArguments();
            Class<?>[] classes = new Class[subTypes.length];
            for (int i = 0; i < subTypes.length; i++) {
                // 获取泛型类型
                Class<?> clazz = (Class<?>)((ParameterizedType) subTypes[i]).getRawType();
                classes[i] = clazz;
            }
            return classes;
        }
        return null;
    }

    /**
     * 获得方法上的注解
     * @param joinPoint 切点连接点
     * @param annotationClazz 注解类型
     * @param <T> 注解类型
     * @return 注解实现
     */
    public static  <T extends Annotation> T getAnnotation(JoinPoint joinPoint, Class<T> annotationClazz) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        T annotation = method.getAnnotation(annotationClazz);
        if (null != annotation) {
            return annotation;
        }
        Class<?> targetClass = joinPoint.getTarget().getClass();
        return targetClass.getDeclaredAnnotation(annotationClazz);
    }

    /**
     * 注入参数
     * @param joinPoint 连接点
     * @param obj       注入值
     * @param type      注解
     * @param check     检查参数列表
     * @return  注入后的参数列表
     */
    public static Object[] injectParam(JoinPoint joinPoint, Object obj, Annotation type, boolean check) {
        Object[] args = joinPoint.getArgs();
        int len = args.length;
        for (int i = 0; i < len; i++) {
            if (obj.getClass().isInstance(args[i])) {
                args[i] = obj;
                return args;
            }
        }
        if (check) {
            throw new IllegalArgumentException("You must add " + obj.getClass().getSimpleName() + " parameter on method " +
                    joinPoint.getSignature().getName() + " before use @" + type.annotationType().getSimpleName() + ".");
        }
        return args;
    }

    /**
     * 根据EL表达式或内置头表达式抽取值
     * @param joinPoint 切面连接点
     * @param express   表达式
     * @return 解析的值
     */
    public static String extractValue(JoinPoint joinPoint, String express) {
        // 解析Http请求头
        if (express.startsWith(":")) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            assert attributes != null;
            String headerName = express.substring(1);
            String value = attributes.getRequest().getHeader(headerName);
            if (StringUtils.isEmpty(headerName) || StringUtils.isEmpty(value)) {
                throw new IllegalArgumentException("Can't find " + headerName + " from HTTP header.");
            }
            return value;
        }

        // 解析EL表达式
        if (express.startsWith("'") || express.startsWith("@") || express.startsWith("#") || express.startsWith("T(") || express.startsWith("args[")) {
            return ELContext.getValue(joinPoint, express);
        }
        return express;
    }


    /**
     * 包装类型智能注入到方法
     * @param target            目标对象
     * @param method            目标方法
     * @param wrapperList       包装对象列表
     * @param wrapperClazz      包装类型
     * @param wrapperBody       获取包装对象的实际业务数据
     * @param wipeWrapperBody   覆写包装对象的实际业务数据
     * @param <T>               包装对象类型
     * @throws IllegalAccessException       非法访问异常
     * @throws InvocationTargetException    方法调用异常
     * @return 返回原有值
     */
    public static <T> Object invokeWithWrapperInject(Object target, Method method, List<T> wrapperList, Class<T> wrapperClazz, Function<T, Object> wrapperBody, BiConsumer<T, Object> wipeWrapperBody) throws IllegalAccessException, InvocationTargetException {
        // 没有参数
        if(method.getParameterTypes().length == 0) {
            return method.invoke(target);
        }
        // ResolvableType是Spring核心包里的泛型解决方案，简化对泛型识别处理（下面注释保留Java API处理方式）
        ResolvableType resolvableType = ResolvableType.forMethodParameter(method, 0);
        // 获取参数类型
        Class<?> parameterClazz = resolvableType.resolve(); // method.getParameterTypes()[0];
        // Map 或 Object
        if (parameterClazz == Map.class || parameterClazz == Object.class) {
            // 去掉实体的包装
            return method.invoke(target, wrapperBody.apply(wrapperList.get(0)));
        }

        // Entity
        if (parameterClazz == wrapperClazz) {
            T entity = wrapperList.get(0);
            ResolvableType[] wrapperGenerics = resolvableType.getGenerics();
            Class<?> elementGenericType = wrapperGenerics[0].resolve();
            // Entity or Entity<Map>
            if (elementGenericType == null || elementGenericType == Map.class) {
                return method.invoke(target, entity);
            }

            // Entity<T>
            Object body = JSONUtil.parse(JSONUtil.serialize(wrapperBody.apply(wrapperList.get(0))), elementGenericType);
            wipeWrapperBody.accept(entity, body);
            return method.invoke(target, entity);
        }

        // List
        if (parameterClazz == List.class) {
            // List
//            Type[] genericParameterTypes = method.getGenericParameterTypes();
//            if (!(genericParameterTypes[0] instanceof ParameterizedType)) {
            ResolvableType[] wrapperGenerics = resolvableType.getGenerics();
            Class<?> elementGenericType = wrapperGenerics[0].resolve();
            if (elementGenericType == null) {
                // 去掉实体的包装
                return method.invoke(target, wrapperList.stream().map(wrapperBody).collect(Collectors.toList()));
            }
            // List<?>
//            ParameterizedType parameterizedType = (ParameterizedType) genericParameterTypes[0];
//            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
//            Type actualTypeArgument = actualTypeArguments[0];
            // List<Map>
//            if (TypeUtil.type2Class(actualTypeArgument) == Map.class) {
            if (elementGenericType == Map.class) {
                // 去掉实体的包装
                return method.invoke(target, wrapperList.stream().map(wrapperBody).collect(Collectors.toList()));
            }
            // List<Entity>
//            if (TypeUtil.type2Class(actualTypeArgument) == wrapperClazz) {
            if (elementGenericType == wrapperClazz) {
                // List<Entity>
                Class<?> entityGenericType = wrapperGenerics[0].getGeneric(0).resolve();
//                if (!(actualTypeArgument instanceof ParameterizedType)) {
                if (entityGenericType == null) {
                    return method.invoke(target, wrapperList);
                }
                // List<Entity<Map>>
//                Type[] subActualTypeArguments = ((ParameterizedType) actualTypeArgument).getActualTypeArguments();
//                if (TypeUtil.type2Class(subActualTypeArguments[0]) == Map.class) {
                if (entityGenericType == Map.class) {
                    return method.invoke(target, wrapperList);
                }
                // List<Entity<T>>
//                JavaType javaType = TypeUtil.type2JavaType(subActualTypeArguments[0]);
                for (T wrapper : wrapperList) {
//                    Object body = JSONUtil.nativeRead(JSONUtil.serialize(wrapperBody.apply(wrapper)), javaType);
                    Object body = JSONUtil.parse(JSONUtil.serialize(wrapperBody.apply(wrapper)), entityGenericType);
                    wipeWrapperBody.accept(wrapper, body);
                }
                return method.invoke(target, wrapperList);
            }

            // List<T>
//            method.invoke(target, wrapperList.stream().map(wrapper ->
//                    JSONUtil.nativeRead(JSONUtil.serialize(wrapperBody.apply(wrapper)), TypeUtil.type2JavaType(actualTypeArgument))).collect(Collectors.toList()));
            return method.invoke(target, wrapperList.stream().map(wrapper ->
                    JSONUtil.parse(JSONUtil.serialize(wrapperBody.apply(wrapper)), elementGenericType)).collect(Collectors.toList()));
        }

        // 转到业务类型 T
        return method.invoke(target, JSONUtil.parse(JSONUtil.serialize(wrapperBody.apply(wrapperList.get(0))), parameterClazz));
    }


    /**
     * 获取方法返回类型，并返回默认类型值
     * @param joinPoint JoinPoint
     * @return  默认类型值
     */
    public static Object getMethodDefaultReturnVal(JoinPoint joinPoint) {
        Class<?> retType = getMethodReturnType(joinPoint);
        // 基本数据类型过滤
        if (Long.class == retType) {
            return -1L;
        }
        if (long.class == retType || int.class == retType || short.class == retType ||
                float.class == retType || double.class == retType || Number.class.isAssignableFrom(retType)) {
            return -1;
        }
        if (boolean.class == retType || Boolean.class.isAssignableFrom(retType)) {
            return false;
        }
        if (byte.class == retType || Byte.class.isAssignableFrom(retType)) {
            return 0;
        }
        if (char.class == retType || Character.class.isAssignableFrom(retType)) {
            return '\0';
        }
        return null;
    }

    /**
     * 获取方法返回值类型
     * @param joinPoint JoinPoint
     * @return  返回值类型
     */
    public static Class<?> getMethodReturnType(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // (Class<?>) signature.getReturnType()
        return getMethodReturnType(signature.getMethod());
    }

    /**
     * 获取方法返回值值类型
     * @param method    Method
     * @return  返回值类型
     */
    public static Class<?> getMethodReturnType(Method method) {
        return ResolvableType.forMethodReturnType(method).resolve();
    }

    /**
     * 获取属性路径值
     * @param target    目标对象
     * @param fieldPath 属性路径
     * @param <T>       值类型
     * @return  属性值
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeFieldPath(Object target, String fieldPath) {
        if (target == null || StringUtils.isEmpty(fieldPath)) {
            return null;
        }
        String[] fieldNames = StringUtils.delimitedListToStringArray(fieldPath, ".");
        for (String fieldName : fieldNames) {
            Field field = ReflectionUtils.findField(Objects.requireNonNull(target).getClass(), fieldName);
            if (field == null) return null;
            ReflectionUtils.makeAccessible(field);
            target = ReflectionUtils.getField(field, target);
        }
        return (T) target;
    }

    /**
     * 设置对象的值
     * @param target    对象
     * @param props     属性Map
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setField(Object target, Map<String, Object> props) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            Field field = ReflectionUtils.findField(target.getClass(), entry.getKey());
            if (field == null) continue;
            ReflectionUtils.makeAccessible(field);
            // String -> Enum
            if (Enum.class.isAssignableFrom(field.getType()) && entry.getValue() instanceof String) {
                ReflectionUtils.setField(field, target, Enum.valueOf((Class<? extends Enum>) field.getType(), (String) entry.getValue()));
            } else if(Long.class.isAssignableFrom(field.getType()) && entry.getValue() instanceof Integer) {
                ReflectionUtils.setField(field, target, Long.valueOf(String.valueOf(entry.getValue())));
            } else if(List.class.isAssignableFrom(field.getType()) && entry.getValue() instanceof Map) {
                ReflectionUtils.setField(field, target, new ArrayList(((LinkedHashMap) entry.getValue()).values()));
            } else {
                ReflectionUtils.setField(field, target, entry.getValue());
            }
        }
    }

    /**
     * 获取方法值
     * @param target        目标对象
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @param args          参数值
     * @param <T>           返回值类型
     * @return  返回值
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        Method method = ReflectionUtils.findMethod(target.getClass(), methodName, paramTypes);
        if (method == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(method);
        return (T) ReflectionUtils.invokeMethod(method, target, args);
    }
}
