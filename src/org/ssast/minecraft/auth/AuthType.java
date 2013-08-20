package org.ssast.minecraft.auth;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;

import org.ssast.minecraft.util.ClassUtil;

public class AuthType {

	private static List<AuthType> values = new ArrayList<AuthType>();

	static {
		new AuthType(MinecraftServerAuth.class);
		new AuthType(OfflineServerAuth.class);
		
		Class<?>[] authClasses = ClassUtil.getClassesFromPackage("org.ssast.minecraft.auth", false);
		
		for(Class<?> authClass : authClasses) {
			if(authClass.getSuperclass() != null &&
				authClass.getSuperclass().equals(ServerAuth.class) &&
				!authClass.equals(MinecraftServerAuth.class) &&
				!authClass.equals(OfflineServerAuth.class)) {
				new AuthType(authClass);
			}
		}
	}

	private String name;
	private Class<?> auth;
	private String alias;

	private AuthType(Class<?> auth) {
		try {
			this.name = (String)auth.getDeclaredMethod("getAuthTypeName").invoke(null);
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.auth = auth;

		try {
			alias = (String)auth.getDeclaredMethod("getAlias").invoke(null);
			values.add(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ServerAuth newInstance(String name, String pass) {
		try {
			Constructor<?> constructor = auth.getConstructor(String.class, String.class);
			ServerAuth result = (ServerAuth)constructor.newInstance(name, pass);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public String toString() {
		return name;
	}

	public String value() {
		return alias;
	}

	public static AuthType valueOf(String value) {
		for(AuthType at : values) {
			if(at.value().equals(value)) {
				return at;
			}
		}
		if(values.size() > 0)
			return values.get(0);
		return null;
	}

	public static List<AuthType> values() {
		return values;
	}

}
