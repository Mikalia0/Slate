package com.slate.android.storage
import org.junit.Assert.*
import org.junit.Test
class CacheCandidatesTest {
    private fun row(name:String,cache:Long)=AppUsage(name,name,false,0,cache,cache)
    @Test fun selectsOnlyMeasuredValidPackagesLargestFirst(){
        val result=cacheCandidates(listOf(row("com.test.small",10),row("com.test.big",50),row("com.test.zero",0),row("--invalid",100),row("com.test.small",10)))
        assertEquals(listOf("com.test.big","com.test.small"),result.map{it.packageName})
        assertEquals(60L,result.sumOf{it.cacheBytes})
    }
}
