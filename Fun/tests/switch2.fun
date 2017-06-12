# Tests contextual errors for switch commands

func int test (int num):
  int result = 0
  switch num:
    case 1:
      result = 10
    case 1..5:            # overlapping values
      result = 15
    case 3..2:            #invalid range
      result = 20
    default:
      result = 25
  .
  return result
.

proc main ():
  write(test(4))
.
