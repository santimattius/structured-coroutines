package io.github.santimattius.structured.sample

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

fun badJobInLaunch(scope: CoroutineScope) {
    scope.launch(Job()) {           // ERROR!
        println("Bad!")
    }
    scope.launch(SupervisorJob()) { // ERROR!
        println("Also bad!")
    }
}

fun inlineScopeCreation() {
    CoroutineScope(Dispatchers.IO).launch {
        println("This is bad!")
    }
}

fun globalScopeUsage() {
    GlobalScope.launch {
        println("This is also bad!")
    }
}