


package test

import "other"

import ( 
  fmt "os";
  "flag"
  "boss/dave"
  _ "dook" 
  // command line option parser 
)

import d "K"

// This is a test comment
func foo()int{
	return 1
}

// Sleep pauses the current goroutine for at least ns nanoseconds.
// Higher resolution sleeping may be provided by syscall.Nanosleep 
// on some operating systems.
func Sleep(ns int64) os.Error {
	_, err := sleep(Nanoseconds(), ns)
	return err
}
