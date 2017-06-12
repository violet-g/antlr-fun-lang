# Tests switch command with int arg

func int test (int num):
  int result = 0
  switch num:
    case 1:
      result = 10
    case 2..7:
      result = 15
    default:
      result = 20
  .
  return result
.

proc main ():
  write(test(6))
.
