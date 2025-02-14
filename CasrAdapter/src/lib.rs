#![no_main]

use jni::objects::{JObject, JValue};
use jni::sys::jobject;
use jni::JNIEnv;
use libcasr::java::JavaStacktrace;
use libcasr::stacktrace::{cluster_stacktraces, ParseStacktrace};

#[no_mangle]
pub extern "C" fn Java_org_jetbrains_casr_adapter_CasrAdapter_parseAndClusterStackTraces(
    mut env: JNIEnv,
    _: JObject,
    raw_stacktraces: jobject, // List<String>
) -> jobject {
    let stacktraces = convert_to_rust_format(&mut env, raw_stacktraces);

    let mut parsed_stacktraces = Vec::new();

    for trace in stacktraces {
        let extracted_stacktrace = match JavaStacktrace::extract_stacktrace(&trace) {
            Ok(traces) => traces,
            Err(err) => {
                eprintln!("Error extracting stacktraces: {}", err);
                continue;
            }
        };

        match JavaStacktrace::parse_stacktrace(&*extracted_stacktrace) {
            Ok(stacktrace) => parsed_stacktraces.push(stacktrace),
            Err(err) => eprintln!("Error parsing stacktrace: {}", err),
        }
    }

    let result = cluster_stacktraces(&*parsed_stacktraces);
    let output = match result {
        Ok(stacktraces) => stacktraces.iter().map(|s| *s as i32).collect::<Vec<i32>>(),
        Err(err) => {
            eprintln!("Error clustering stacktraces: {}", err);
            Vec::new()
        }
    };

    let java_result = env
        .new_object(
            "java/util/ArrayList",
            "(I)V",
            &[JValue::from(output.len() as i32)],
        )
        .unwrap();

    for value in output {
        let java_value = env
            .new_object("java/lang/Integer", "(I)V", &[JValue::from(value)])
            .unwrap();

        let _ = env
            .call_method(
                &java_result,
                "add",
                "(Ljava/lang/Object;)Z",
                &[JValue::Object(&java_value)],
            )
            .unwrap();
    }

    *java_result
}

fn convert_to_rust_format(
    env: &mut JNIEnv,
    java_list: jobject // List<String>
) -> Vec<String> {
    let java_list_obj = unsafe { JObject::from_raw(java_list) };

    let iterator = env
        .call_method(&java_list_obj, "iterator", "()Ljava/util/Iterator;", &[])
        .unwrap()
        .l()
        .unwrap();

    let mut result = Vec::new();

    while env
        .call_method(&iterator, "hasNext", "()Z", &[])
        .unwrap()
        .z()
        .unwrap()
    {
        let item = env
            .call_method(&iterator, "next", "()Ljava/lang/Object;", &[])
            .unwrap()
            .l()
            .unwrap();
        let item_str = env
            .call_method(&item, "toString", "()Ljava/lang/String;", &[])
            .unwrap()
            .l()
            .unwrap();
        let item_rust_str: String = env.get_string((&item_str).into()).unwrap().into();

        result.push(item_rust_str);
    }

    result
}
