package com.exchangerate.core.structure

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import com.crashlytics.android.Crashlytics
import com.exchangerate.core.data.live.LiveDataReactiveConverter
import com.exchangerate.core.structure.middleware.CrashMiddleware
import com.exchangerate.core.structure.middleware.LoggerMiddleware
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject

interface MviAction

interface MviIntent

interface MviProcessor

interface MviState

interface MviView<I : MviIntent, in S : MviState> {

    fun intents(): Observable<I>

    fun render(state: S?)
}

interface MviRenderer<in S : MviState, in V : MviView<*, *>> {

    fun render(state: S?, view: V)
}

open class MviViewModel<I : MviIntent, A : MviAction, S : MviState>(
        private val filter: MviFilter<I, S>,
        private val interpreter: MviIntentInterpreter<I, A>,
        private val router: MviRouter<A>,
        private val store: MviStore<S>
) : ViewModel() {

    private val liveState: LiveData<S> by lazy {
        LiveDataReactiveConverter.fromPublisher(store.stateObserver)
    }

    private lateinit var disposable: Disposable

    fun processIntents(intents: Observable<I>) {
        disposable = intents
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .filter { intent -> filter.apply(intent, liveState.value) }
                .flatMap { intent -> Observable.fromIterable(interpreter.translate(intent)) }
                .flatMap { action -> router.route(action) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { error -> Crashlytics.logException(error) }
                .subscribe()
    }

    fun liveStates(): LiveData<S> = liveState

    override fun onCleared() {
        super.onCleared()
        disposable.dispose()
    }
}

interface MviIntentInterpreter<in I : MviIntent, out A : MviAction> {

    fun translate(intent: I): List<A>
}

interface MviFilter<in I : MviIntent, in S : MviState> {

    fun apply(intent: I, state: S?): Boolean
}

interface MviRouter<in A : MviAction> {

    fun route(action: A): Observable<Unit>
}

interface MviReducer<S : MviState> {

    fun reduce(action: MviAction, state: S): S

    fun initialState(): S
}

interface MviMiddleware {

    fun intercept(oldState: MviState, action: MviAction, newState: MviState? = null)
}

class MviStore<S : MviState>(private val reducer: MviReducer<S>) {

    private var state = reducer.initialState()
    private val middleware: List<MviMiddleware> = listOf(
            LoggerMiddleware(), CrashMiddleware()
    )

    val stateObserver: BehaviorSubject<S> = BehaviorSubject
            .createDefault(reducer.initialState())

    fun next(action: MviAction) {
        middleware.forEach { it.intercept(state, action) }
    }

    fun dispatch(action: MviAction) {
        val newState = reducer.reduce(action, state)
        middleware.forEach { it.intercept(state, action, newState) }
        state = newState
        stateObserver.onNext(state)
    }
}
