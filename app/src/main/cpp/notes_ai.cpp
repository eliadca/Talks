#include <jni.h>
#include "llama.h"
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

static std::once_flag backend;
static bool aborted(void * data) { return static_cast<std::atomic_bool *>(data)->load(); }
extern "C" JNIEXPORT jlong JNICALL Java_com_eliadca_talks_notesai_NativeNotesAi_newRequest(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new std::atomic_bool(false));
}
extern "C" JNIEXPORT void JNICALL Java_com_eliadca_talks_notesai_NativeNotesAi_cancel(JNIEnv *, jobject, jlong request) {
    reinterpret_cast<std::atomic_bool *>(request)->store(true);
}
extern "C" JNIEXPORT void JNICALL Java_com_eliadca_talks_notesai_NativeNotesAi_freeRequest(JNIEnv *, jobject, jlong request) {
    delete reinterpret_cast<std::atomic_bool *>(request);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_eliadca_talks_notesai_NativeNotesAi_generate(JNIEnv * env, jobject, jstring path, jbyteArray input, jlong request) {
    try {
        std::call_once(backend, [] { llama_backend_init(); });
        auto * cancel = reinterpret_cast<std::atomic_bool *>(request);
        const char * filename = env->GetStringUTFChars(path, nullptr);
        std::string model_path(filename); env->ReleaseStringUTFChars(path, filename);
        std::string prompt(env->GetArrayLength(input), '\0');
        env->GetByteArrayRegion(input, 0, prompt.size(), reinterpret_cast<jbyte *>(prompt.data()));
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(model_path.c_str(), mp), llama_model_free);
        if (!model) throw std::runtime_error("No se pudo cargar el modelo de IA. Comprueba el espacio y la memoria disponibles.");
        if (cancel->load()) return nullptr;
        const auto * vocab = llama_model_get_vocab(model.get());
        int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        if (count <= 0 || count > 1400) throw std::runtime_error("Selecciona un fragmento más corto para revisar.");
        std::vector<llama_token> tokens(count);
        llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), true, true);
        auto cp = llama_context_default_params(); cp.n_ctx = 2048; cp.n_batch = 256;
        cp.n_threads = 2; cp.n_threads_batch = 2; cp.no_perf = true;
        cp.abort_callback = aborted; cp.abort_callback_data = cancel;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx) throw std::runtime_error("No hay memoria suficiente para iniciar la IA local.");
        std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(llama_batch_ext_init(ctx.get()),llama_batch_ext_free);
        auto fill = [&](const llama_token * input, int n, int position) {
            llama_batch_ext_clear(batch.get());
            for(int i=0;i<n;++i) { int index=llama_batch_ext_add_token(batch.get(),0,input[i]);llama_pos pos=position+i;llama_batch_ext_set_pos(batch.get(),index,&pos); }
            llama_batch_ext_set_output_logits(batch.get(),n-1,true);
        };
        for (int pos = 0; pos < count; pos += 256) {
            if (cancel->load()) return nullptr;
            fill(tokens.data()+pos,std::min(256,count-pos),pos);
            if (llama_process(ctx.get(),LLAMA_PROCESS_TYPE_DECODE,batch.get()) != 0) { if (cancel->load()) return nullptr; throw std::runtime_error("No se pudo analizar el fragmento."); }
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_greedy());
        const std::string marker="<|im_start|>user\n";
        auto user_start=prompt.rfind(marker);auto user_end=prompt.find("<|im_end|>",user_start);
        int user_tokens=count;
        if(user_start!=std::string::npos && user_end!=std::string::npos) {
            user_start+=marker.size();
            user_tokens=-llama_tokenize(vocab,prompt.data()+user_start,user_end-user_start,nullptr,0,false,false);
        }
        const int maximum=std::min(600,std::max(64,user_tokens*2+24));
        std::string output;
        for (int i=0; i<maximum; ++i) {
            if (cancel->load()) return nullptr;
            auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) {
                auto result = env->NewByteArray(output.size());
                env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
                return result;
            }
            char piece[256]; int n=llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (n < 0) { std::vector<char> large(-n); n=llama_token_to_piece(vocab, token, large.data(), large.size(), 0, false); if (n>0) output.append(large.data(), n); }
            else output.append(piece,n);
            fill(&token,1,count+i);
            if (llama_process(ctx.get(),LLAMA_PROCESS_TYPE_DECODE,batch.get()) != 0) { if (cancel->load()) return nullptr; throw std::runtime_error("No se pudo completar la corrección."); }
        }
        throw std::runtime_error("La propuesta quedó incompleta. Selecciona un fragmento más corto.");
    } catch (const std::exception & e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); return nullptr;
    }
}
