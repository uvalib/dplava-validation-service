<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0"
  xmlns="http://www.music-encoding.org/ns/mei" xmlns:mei="http://www.music-encoding.org/ns/mei"
  xmlns:xlink="http://www.w3.org/1999/xlink" exclude-result-prefixes="mei xlink">

  <xsl:output method="text"/>
  <xsl:strip-space elements="*"/>

  <!-- ======================================================================= -->
  <!-- PARAMETERS                                                              -->
  <!-- ======================================================================= -->

  <!-- ======================================================================= -->
  <!-- GLOBAL VARIABLES                                                        -->
  <!-- ======================================================================= -->

  <!-- program name -->
  <xsl:variable name="progname">
    <xsl:text>checkEDTF.xsl</xsl:text>
  </xsl:variable>

  <!-- program version -->
  <xsl:variable name="version">
    <xsl:text>1.0 beta</xsl:text>
  </xsl:variable>

  <!-- program id -->
  <xsl:variable name="progid">
    <xsl:value-of select="concat('app_', format-dateTime(current-dateTime(), '[Y][d][H][m][s][f]'))"
    />
  </xsl:variable>

  <!-- new line -->
  <xsl:variable name="nl">
    <xsl:text>&#xa;</xsl:text>
  </xsl:variable>

  <!-- ======================================================================= -->
  <!-- UTILITIES / NAMED TEMPLATES                                             -->
  <!-- ======================================================================= -->

  <xsl:template name="validDate">
    <xsl:param name="dateString"/>

    <xsl:choose>

      <!-- Level 0 and 1 -->
      <!-- Single date with null value -->
      <xsl:when test="matches($dateString, '^$')"/>

      <!-- Single ISO 8601-1 date, date + time or letter-prefixed year, seasons, 
        qualification of a complete date, and unspecified digits from the right -->
      <xsl:when
        test="
          matches($dateString, '^-?([1-9]X{0,3}|[1-9]\d{1}X{0,2}|[1-9]\d{2}X{0,1}|[1-9]\d{3}|Y[1-9]\d{4,}|Y[1-9]\d{3}X|Y[1-9]\d{2}XX+|Y[1-9]\dXXX+)(-(21|22|23|24))?(~|\?|%)?$') or
          matches($dateString, '^-?([1-9]X{0,3}|[1-9]\d{1}X{0,2}|[1-9]\d{2}X{0,1}|[1-9]\d{3}|Y[1-9]\d{4,}|Y[1-9]\d{3}X|Y[1-9]\d{2}XX+|Y[1-9]\dXXX+)(-(01|03|05|07|08|10|12|XX)(-(0[1-9]|[1-2][0-9]|3[0-1]|XX))?)?(~|\?|%)?$') or
          matches($dateString, '^-?([1-9]X{0,3}|[1-9]\d{1}X{0,2}|[1-9]\d{2}X{0,1}|[1-9]\d{3}|Y[1-9]\d{4,}|Y[1-9]\d{3}X|Y[1-9]\d{2}XX+|Y[1-9]\dXXX+)(-(04|06|09|11)(-(0[1-9]|[1-2][0-9]|30|XX))?)?(~|\?|%)?$') or
          matches($dateString, '^-?([1-9]X{0,3}|[1-9]\d{1}X{0,2}|[1-9]\d{2}X{0,1}|[1-9]\d{3}|Y[1-9]\d{4,}|Y[1-9]\d{3}X|Y[1-9]\d{2}XX+|Y[1-9]\dXXX+)(-(02)(-(0[1-9]|[1-2][0-9]|XX))?)?(~|\?|%)?$') or
          matches($dateString, '^-?([1-9]\d{0,3}|Y[1-9]\d{4,})-(((01|03|05|07|08|10|12)-(0[1-9]|[1-2][0-9]|3[0-1]))|((04|06|09|11)(-(0[1-9]|[1-2][0-9]|30))|((02)(-(0[1-9]|[1-2][0-9])))))(T([01][0-9]|2[0-3]):([0-5][0-9])(:([0-5][0-9]|60)([\.,]\d+)?)?(Z|(\+|-)([01][0-9]|2[0-3])((:[0-5][0-9])(:[0-5][0-9]([\.,]\d+)?)?)?)?)?(~|\?|%)?$')"/>

      <!-- Level 2 -->
      <!-- Single exponential year or year with optional significant digits -->
      <xsl:when
        test="
          matches($dateString, '^-?[1-9][0-9]{0,3}(S[1-9][0-9]*)?$') or
          matches($dateString, '^-?[1-9][0-9]{0,3}(S[1-9][0-9]*)?$') or
          matches($dateString, '^Y-?[1-9][0-9]*E[1-9][0-9]*(S[1-9][0-9]*)?$') or
          matches($dateString, '^-?Y[1-9][0-9]{4,}(S[1-9][0-9]*)?$')"/>

      <!-- Additional sub-year groupings (with optional qualifiers) -->
      <xsl:when
        test="matches($dateString, '^-?([1-9]X{0,3}|[1-9]\d{1}X{0,2}|[1-9]\d{2}X{0,1}|[1-9]\d{3}|Y[1-9]\d{4,}|Y[1-9]\d{3}X|Y[1-9]\d{2}XX+|Y[1-9]\dXXX+)(-(~|\?|%)?(2[1-9]|3[0-9]|4[0-1]))?(~|\?|%)?$')"/>

      <!-- Single date with trailing qualifications of groups and individual components -->
      <xsl:when
        test="
          matches($dateString, '^-?([1-9]\d{0,3}|Y[1-9]\d{4,})(~|\?|%)?(-(01|03|05|07|08|10|12)(~|\?|%)?(-(0[1-9]|[1-2][0-9]|3[0-1])(~|\?|%)?)?)?$') or
          matches($dateString, '^-?([1-9]\d{0,3}|Y[1-9]\d{4,})(~|\?|%)?(-(04|06|09|11)(~|\?|%)?(-(0[1-9]|[1-2][0-9]|30)(~|\?|%)?)?)?$') or
          matches($dateString, '^-?([1-9]\d{0,3}|Y[1-9]\d{4,})(~|\?|%)?(-(02)(~|\?|%)?(-(0[1-9]|[1-2][0-9])(~|\?|%)?)?)?$')"/>

      <!-- Single date with leading qualifications of groups and individual components -->
      <xsl:when
        test="
          matches($dateString, '^-?(~|\?|%)?([1-9]\d{0,3}|Y[1-9]\d{4,})(-(~|\?|%)?(01|03|05|07|08|10|12)(-(~|\?|%)?(0[1-9]|[1-2][0-9]|3[0-1]))?)?$') or
          matches($dateString, '^-?(~|\?|%)?([1-9]\d{0,3}|Y[1-9]\d{4,})(-(~|\?|%)?(04|06|09|11)(-(~|\?|%)?(0[1-9]|[1-2][0-9]|30))?)?$') or
          matches($dateString, '^-?(~|\?|%)?([1-9]\d{0,3}|Y[1-9]\d{4,})(-(~|\?|%)?(02)(-(~|\?|%)?(0[1-9]|[1-2][0-9]))?)?$')"/>

      <!-- Single year with unspecified digits with optional sub-year component, with optional trailing qualifier -->
      <xsl:when
        test="
          matches($dateString, '^-?([\dX]{1,4}|Y[\dX]{5,})(-(~|\?|%)?(2[1-9]|3[0-9]|4[0-1]))?(~|\?|%)?$')"/>

      <!-- Single date with complete month but unspecified digits elsewhere -->
      <xsl:when
        test="
          matches($dateString, '^-?([\dX]{1,4}|Y[\dX]{5,})(-(01|03|05|07|08|10|12)(-(0[1-9X]|[1-2][0-9X]|3[0-1X]|X\d|XX))?)?$') or
          matches($dateString, '^-?([\dX]{1,4}|Y[\dX]{5,})(-(04|06|09|11)(-(0[1-9X]|[1-2][0-9X]|3[0X]|X\d|XX))?)?$') or
          matches($dateString, '^-?([\dX]{1,4}|Y[\dX]{5,})(-(02)(-(0[1-9X]|[1-2][0-9X]|X\d|XX))?)?$')"/>

      <!-- Single date with unspecified digits anywhere -->
      <xsl:when
        test="matches($dateString, '^-?([\dX]{1,4}|Y[\dX]{5,})(-(0[1-9X]|1[0-2X]|X[0-2X])(-(0[1-9X]|[1-2][0-9X]|3[0-1X]|X\d|XX))?)?$')"/>

      <!-- Anything else is invalid -->
      <xsl:otherwise>
        <xsl:call-template name="warning">
          <xsl:with-param name="warningText">"<xsl:value-of select="$dateString"/>"</xsl:with-param>
        </xsl:call-template>
      </xsl:otherwise>

    </xsl:choose>

  </xsl:template>

  <xsl:template name="validDateRange">
    <xsl:param name="dateString"/>
    <xsl:variable name="startDate" select="substring-before($dateString, '/')"/>
    <xsl:variable name="endDate" select="substring-after($dateString, '/')"/>

    <xsl:choose>

      <!-- Too many slashes -->
      <xsl:when test="matches($dateString, '/.*/')">
        <xsl:call-template name="warning">
          <xsl:with-param name="warningText">"<xsl:value-of select="$dateString"/>"</xsl:with-param>
        </xsl:call-template>
      </xsl:when>

      <!-- When start date is open (..) or unknown ('') -->
      <xsl:when test="matches($startDate, '^\.\.$') or matches($startDate, '^$')">
        <xsl:choose>

          <!-- Date range is invalid if both start and end are open ('..') or unknown ('') -->
          <xsl:when test="matches($endDate, '^\.\.$') or matches($endDate, '^$')">
            <xsl:call-template name="warning">
              <xsl:with-param name="warningText">"<xsl:value-of select="$dateString"
                />"</xsl:with-param>
            </xsl:call-template>
          </xsl:when>

          <!-- Check validity of end date -->
          <xsl:otherwise>
            <xsl:call-template name="validDate">
              <xsl:with-param name="dateString">
                <xsl:value-of select="$endDate"/>
              </xsl:with-param>
            </xsl:call-template>
          </xsl:otherwise>

        </xsl:choose>
      </xsl:when>

      <!-- When end date is open (..) or unknown ('') -->
      <xsl:when test="matches($endDate, '^\.\.$') or matches($endDate, '^$')">
        <xsl:choose>

          <!-- Date range is invalid if both start and end are open ('..') or unknown ('') -->
          <xsl:when test="matches($startDate, '^\.\.$') or matches($startDate, '^$')">
            <xsl:call-template name="warning">
              <xsl:with-param name="warningText">"<xsl:value-of select="$dateString"
                />"</xsl:with-param>
            </xsl:call-template>
          </xsl:when>

          <!-- Check validity of start date -->
          <xsl:otherwise>
            <xsl:call-template name="validDate">
              <xsl:with-param name="dateString">
                <xsl:value-of select="$startDate"/>
              </xsl:with-param>
            </xsl:call-template>
          </xsl:otherwise>

        </xsl:choose>
      </xsl:when>

      <!-- Neither start or end date is open (..) or unknown ('') -->
      <xsl:otherwise>

        <!-- Check validity of start date -->
        <xsl:call-template name="validDate">
          <xsl:with-param name="dateString">
            <xsl:value-of select="$startDate"/>
          </xsl:with-param>
        </xsl:call-template>

        <!-- Check validity of end date -->
        <xsl:call-template name="validDate">
          <xsl:with-param name="dateString">
            <xsl:value-of select="$endDate"/>
          </xsl:with-param>
        </xsl:call-template>

        <!-- Check start/end order? -->
        <xsl:variable name="validRange">
          <!-- Check validity of start date -->
          <xsl:call-template name="validDate">
            <xsl:with-param name="dateString">
              <xsl:value-of select="$startDate"/>
            </xsl:with-param>
          </xsl:call-template>

          <!-- Check validity of end date -->
          <xsl:call-template name="validDate">
            <xsl:with-param name="dateString">
              <xsl:value-of select="$endDate"/>
            </xsl:with-param>
          </xsl:call-template>
        </xsl:variable>

        <xsl:if test="normalize-space($validRange) eq ''">
          <xsl:variable name="cmpStart">
            <xsl:choose>
              <xsl:when test="matches($startDate, 'E')">
                <xsl:variable name="expression">
                  <xsl:value-of select="replace(replace($startDate, 'S\d+', ''), 'Y', '')"/>
                </xsl:variable>
                <xsl:choose>
                  <xsl:when test="matches($startDate, 'S')">
                    <xsl:variable name="signifDigits">
                      <xsl:value-of select="substring-after($startDate, 'S')"/>
                    </xsl:variable>
                    <xsl:variable name="significand">
                      <xsl:value-of select="substring-before(replace($expression, '-', ''), 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="sign">
                      <xsl:if test="matches($expression, '-')">
                        <xsl:text>-</xsl:text>
                      </xsl:if>
                    </xsl:variable>
                    <xsl:variable name="exponent">
                      <xsl:value-of select="substring-after($expression, 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="significandLength">
                      <xsl:value-of select="string-length(replace($significand, '-', ''))"/>
                    </xsl:variable>
                    <xsl:value-of
                      select="number(concat($sign, concat(substring(concat(substring($significand, 1, $signifDigits), '0000000000'), 1, $significandLength), 'E', $exponent)))"
                    />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:variable name="significand">
                      <xsl:value-of select="substring-before(replace($expression, '-', ''), 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="sign">
                      <xsl:if test="matches($expression, '-')">
                        <xsl:text>-</xsl:text>
                      </xsl:if>
                    </xsl:variable>
                    <xsl:variable name="exponent">
                      <xsl:value-of select="substring-after($expression, 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="significandLength">
                      <xsl:value-of select="string-length(replace($significand, '-', ''))"/>
                    </xsl:variable>
                    <xsl:value-of
                      select="number(concat($sign, $significand, '0000000000E', $exponent))"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:when>
              <xsl:when test="matches($startDate, 'Y')">
                <xsl:variable name="normalizeString">
                  <xsl:if test="matches($startDate, '^-')">
                    <xsl:text>-</xsl:text>
                  </xsl:if>
                  <xsl:value-of
                    select="
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      substring-after($startDate, 'Y'), '[\+\-]\d{2}:\d{2}$', ''),
                      '[~\?%]', ''),
                      'S\d+', ''),
                      '[T:\-\+]', '/'),
                      'Z', ''),
                      'X', '0'),
                      '^/', '-')
                      "/>
                  <xsl:if test="not(matches($startDate, '[^\-]-'))">
                    <xsl:text>/</xsl:text>
                  </xsl:if>
                </xsl:variable>
                <xsl:variable name="year">
                  <xsl:value-of select="replace(substring-before($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="remainder">
                  <xsl:value-of select="replace(substring-after($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="datetimePart">
                  <xsl:value-of select="replace($remainder, '\.\d+', '')"/>
                </xsl:variable>
                <xsl:variable name="fractionalSeconds">
                  <xsl:value-of select="substring-after($remainder, '.')"/>
                </xsl:variable>
                <xsl:choose>
                  <xsl:when test="string-length(replace($datetimePart, '^-', '')) &lt; 10">
                    <xsl:value-of
                      select="concat($year, substring(concat($datetimePart, '0000000000'), 1, 10), $fractionalSeconds)"
                    />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="concat($year, $datetimePart, '.', $fractionalSeconds)"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:when>
              <xsl:when test="matches($startDate, '^$') or matches($startDate, '^\.\.$')">
                <xsl:value-of select="0"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:variable name="normalizeString">
                  <xsl:value-of
                    select="
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      $startDate, '[\+\-]\d{2}:\d{2}$', ''),
                      '[~\?%]', ''),
                      'S\d+', ''),
                      '[T:\-\+]', '/'),
                      'Z', ''),
                      'X', '0'),
                      '^/', '-')
                      "/>
                  <xsl:if test="not(matches($startDate, '[^\-]-'))">
                    <xsl:text>/</xsl:text>
                  </xsl:if>
                </xsl:variable>
                <xsl:variable name="year">
                  <xsl:value-of select="replace(substring-before($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="remainder">
                  <xsl:value-of select="replace(substring-after($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="datetimePart">
                  <xsl:value-of select="replace($remainder, '\.\d+', '')"/>
                </xsl:variable>
                <xsl:variable name="fractionalSeconds">
                  <xsl:value-of select="substring-after($remainder, '.')"/>
                </xsl:variable>
                <xsl:choose>
                  <xsl:when test="string-length(replace($datetimePart, '^-', '')) &lt; 10">
                    <xsl:value-of
                      select="concat($year, substring(concat($datetimePart, '0000000000'), 1, 10), $fractionalSeconds)"
                    />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="concat($year, $datetimePart, '.', $fractionalSeconds)"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          <xsl:variable name="cmpEnd">
            <xsl:choose>
              <xsl:when test="matches($endDate, 'E')">
                <xsl:variable name="expression">
                  <xsl:value-of select="replace(replace($endDate, 'S\d+', ''), 'Y', '')"/>
                </xsl:variable>
                <xsl:choose>
                  <xsl:when test="matches($endDate, 'S')">
                    <xsl:variable name="signifDigits">
                      <xsl:value-of select="substring-after($endDate, 'S')"/>
                    </xsl:variable>
                    <xsl:variable name="significand">
                      <xsl:value-of select="substring-before(replace($expression, '-', ''), 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="sign">
                      <xsl:if test="matches($expression, '-')">
                        <xsl:text>-</xsl:text>
                      </xsl:if>
                    </xsl:variable>
                    <xsl:variable name="exponent">
                      <xsl:value-of select="substring-after($expression, 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="significandLength">
                      <xsl:value-of select="string-length(replace($significand, '-', ''))"/>
                    </xsl:variable>
                    <xsl:value-of
                      select="number(concat($sign, concat(substring(concat(substring($significand, 1, $signifDigits), '0000000000'), 1, $significandLength), 'E', $exponent)))"
                    />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:variable name="significand">
                      <xsl:value-of select="substring-before(replace($expression, '-', ''), 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="sign">
                      <xsl:if test="matches($expression, '-')">
                        <xsl:text>-</xsl:text>
                      </xsl:if>
                    </xsl:variable>
                    <xsl:variable name="exponent">
                      <xsl:value-of select="substring-after($expression, 'E')"/>
                    </xsl:variable>
                    <xsl:variable name="significandLength">
                      <xsl:value-of select="string-length(replace($significand, '-', ''))"/>
                    </xsl:variable>
                    <xsl:value-of
                      select="number(concat($sign, $significand, '0000000000E', $exponent))"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:when>
              <xsl:when test="matches($endDate, 'Y')">
                <xsl:variable name="normalizeString">
                  <xsl:if test="matches($endDate, '^-')">
                    <xsl:text>-</xsl:text>
                  </xsl:if>
                  <xsl:value-of
                    select="
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      substring-after($endDate, 'Y'), '[\+\-]\d{2}:\d{2}$', ''),
                      '[~\?%]', ''),
                      'S\d+', ''),
                      '[T:\-\+]', '/'),
                      'Z', ''),
                      'X', '9'),
                      '^/', '-')
                      "/>
                  <xsl:if test="not(matches($endDate, '[^\-]-'))">
                    <xsl:text>/</xsl:text>
                  </xsl:if>
                </xsl:variable>
                <xsl:variable name="year">
                  <xsl:value-of select="replace(substring-before($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="remainder">
                  <xsl:value-of select="replace(substring-after($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="datetimePart">
                  <xsl:value-of select="replace($remainder, '\.\d+', '')"/>
                </xsl:variable>
                <xsl:variable name="fractionalSeconds">
                  <xsl:value-of select="substring-after($remainder, '.')"/>
                </xsl:variable>
                <xsl:choose>
                  <xsl:when test="string-length(replace($datetimePart, '^-', '')) &lt; 10">
                    <xsl:value-of
                      select="concat($year, substring(concat($datetimePart, '0000000000'), 1, 10), $fractionalSeconds)"
                    />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="concat($year, $datetimePart, '.', $fractionalSeconds)"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:when>
              <xsl:when test="matches($endDate, '^$') or matches($endDate, '^\.\.$')">
                <xsl:value-of select="0"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:variable name="normalizeString">
                  <xsl:value-of
                    select="
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      replace(
                      $endDate, '[\+\-]\d{2}:\d{2}$', ''),
                      '[~\?%]', ''),
                      'S\d+', ''),
                      '[T:\-\+]', '/'),
                      'Z', ''),
                      'X', '9'),
                      '^/', '-')
                      "/>
                  <xsl:if test="not(matches($endDate, '[^\-]-'))">
                    <xsl:text>/</xsl:text>
                  </xsl:if>
                </xsl:variable>
                <xsl:variable name="year">
                  <xsl:value-of select="replace(substring-before($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="remainder">
                  <xsl:value-of select="replace(substring-after($normalizeString, '/'), '/', '')"/>
                </xsl:variable>
                <xsl:variable name="datetimePart">
                  <xsl:value-of select="replace($remainder, '\.\d+', '')"/>
                </xsl:variable>
                <xsl:variable name="fractionalSeconds">
                  <xsl:value-of select="substring-after($remainder, '.')"/>
                </xsl:variable>
                <xsl:choose>
                  <xsl:when test="string-length(replace($datetimePart, '^-', '')) &lt; 10">
                    <xsl:value-of
                      select="concat($year, substring(concat($datetimePart, '0000000000'), 1, 10), $fractionalSeconds)"
                    />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="concat($year, $datetimePart, '.', $fractionalSeconds)"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:variable>

          <!-- Issue error message if end date is greater than start date -->
          <xsl:if test="number($cmpEnd) &lt; number($cmpStart)">
            <xsl:value-of
              select="concat('Range start (&quot;', $startDate, '&quot;) later than range end (&quot;', $endDate, '&quot;)&#xa;')"
            />
          </xsl:if>

        </xsl:if>

      </xsl:otherwise>
    </xsl:choose>

  </xsl:template>

  <!-- Display a warning message -->
  <xsl:template name="warning">
    <xsl:param name="warningText"/>
    <text>
      <xsl:value-of select="normalize-space($warningText)"/>
      <xsl:value-of select="$nl"/>
    </text>
  </xsl:template>

  <!-- ======================================================================= -->
  <!-- MAIN OUTPUT TEMPLATE                                                    -->
  <!-- ======================================================================= -->

  <xsl:template match="/">
    <!-- Capture textual output from templates -->
    <xsl:variable name="errors">
      <xsl:apply-templates/>
    </xsl:variable>

    <!-- If $errors isn't empty, display $errors -->
    <xsl:if test="normalize-space($errors) ne ''">
      <xsl:text>Invalid EDTF</xsl:text>
      <xsl:value-of select="$nl"/>
      <xsl:text>------------</xsl:text>
      <xsl:value-of select="$nl"/>
      <xsl:value-of select="$errors"/>
    </xsl:if>

  </xsl:template>

  <!-- ======================================================================= -->
  <!-- MATCH TEMPLATES FOR ELEMENTS                                            -->
  <!-- ======================================================================= -->

  <xsl:template match="*:created">
    <xsl:variable name="dateTime">
      <xsl:value-of select="replace(., '\s', '')"/>
    </xsl:variable>

    <xsl:choose>

      <!-- Content is a single date expression -->
      <xsl:when test="not(matches(., '/')) and not(matches(., '\[')) and not(matches(., '\{'))">
        <xsl:call-template name="validDate">
          <xsl:with-param name="dateString">
            <xsl:value-of select="$dateTime"/>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:when>

      <!-- Content is a date range expression -->
      <xsl:when test="matches(., '/')">
        <xsl:call-template name="validDateRange">
          <xsl:with-param name="dateString">
            <xsl:value-of select="$dateTime"/>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:when>

      <!-- Content is a date set -->
      <xsl:when test="matches(., '^\[[^\]]+\]$') or matches(., '^\{[^\}]+\}$')">

        <!-- dateSet is the expression without brackets -->
        <xsl:variable name="dateSet">
          <xsl:value-of
            select="replace(replace(replace(replace(., '\[', ''), '\]', ''), '\{', ''), '\}', '')"/>
        </xsl:variable>

        <xsl:choose>
          <!-- Spaces, adjoining dots and commas, commas at beginning or end of the expression,
            dots at beginning and end of the expression without an intervening comma and
            successive dots without an intervening comma are invalid -->
          <xsl:when
            test="
              matches($dateSet, '&#32;') or matches($dateSet, '[\.]{3}') or
              matches($dateSet, '[,]{2}') or matches($dateSet, ',\.\.') or matches($dateSet, '\.\.,') or
              matches($dateSet, '^,') or matches($dateSet, ',$') or
              matches($dateSet, '\.\.[^,]*?\.\.') or
              (matches($dateSet, '^\.\..*\.\.$') and not(matches($dateSet, ',')))">
            <text>"<xsl:value-of select="."/>"<xsl:value-of select="$nl"/></text>
          </xsl:when>

          <!-- Check other values -->
          <xsl:otherwise>
            <xsl:choose>
              <!-- Date set contains commas and/or '..' -->
              <xsl:when test="matches($dateSet, '(,|\.\.)')">
                <xsl:analyze-string select="$dateSet" regex="(,|\.\.)">
                  <xsl:non-matching-substring>
                    <xsl:call-template name="validDate">
                      <xsl:with-param name="dateString">
                        <xsl:value-of select="."/>
                      </xsl:with-param>
                    </xsl:call-template>
                  </xsl:non-matching-substring>
                </xsl:analyze-string>
              </xsl:when>

              <!-- Other values are invalid -->
              <xsl:otherwise>
                <text>"<xsl:value-of select="$dateTime"/>"<xsl:value-of select="$nl"/></text>
              </xsl:otherwise>

            </xsl:choose>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>

      <!-- Other values are invalid -->
      <xsl:otherwise>
        <text>"<xsl:value-of select="$dateTime"/>"<xsl:value-of select="$nl"/></text>
      </xsl:otherwise>

    </xsl:choose>
  </xsl:template>

  <!-- ======================================================================= -->
  <!-- DEFAULT TEMPLATE                                                        -->
  <!-- ======================================================================= -->

  <xsl:template match="@* | node()">
    <xsl:apply-templates select="@* | node()"/>
  </xsl:template>

</xsl:stylesheet>
